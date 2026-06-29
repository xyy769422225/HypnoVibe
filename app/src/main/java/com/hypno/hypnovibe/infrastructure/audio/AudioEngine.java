package com.hypno.hypnovibe.infrastructure.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import java.nio.ByteBuffer;

/**
 * 纯 Java 音频引擎：MediaExtractor → MediaCodec(流式解码) → AudioTrack(流式播放)。
 * 零 native 依赖，进度精确到采样级，多声道由系统自动 downmix。
 */
public class AudioEngine {
    private static final String TAG = "AudioEngine";

    private MediaExtractor extractor;
    private MediaCodec codec;
    private AudioTrack audioTrack;
    private HandlerThread audioThread;
    private Handler audioHandler;

    private int sampleRate;
    private int channelCount;
    private long durationMs;
    private long durationUs;             // MediaExtractor 使用微秒
    private volatile long seekOffsetMs;  // seek 后补偿 headPosition 归零
    private volatile boolean isPlaying;
    private volatile boolean isReleased;
    private volatile boolean sawOutputEOS;
    /** seek 请求标志，decodeLoop 检测后在自身线程执行 flush */
    private volatile boolean seekRequested;
    private volatile long seekTargetMs;

    // ── 公开 API ──

    /** 创建引擎实例 */
    public AudioEngine() {}

    /**
     * 加载音频文件，自动获取格式信息并构造 AudioTrack。
     * 应在后台线程调用。
     */
    public boolean loadFile(Context ctx, String path) {
        releaseInternal();

        extractor = new MediaExtractor();
        try {
            if (path.startsWith("content://")) {
                android.os.ParcelFileDescriptor pfd =
                    ctx.getContentResolver().openFileDescriptor(Uri.parse(path), "r");
                if (pfd == null) throw new RuntimeException("openFileDescriptor returned null");
                extractor.setDataSource(pfd.getFileDescriptor(), 0, pfd.getStatSize());
                pfd.close();
            } else {
                extractor.setDataSource(path);
            }
        } catch (Exception e) {
            Log.e(TAG, "setDataSource failed: " + path, e);
            try { extractor.release(); } catch (Exception ignored) {}
            extractor = null;
            return false;
        }

        int audioTrackIdx = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIdx = i;
                format = f;
                break;
            }
        }
        if (audioTrackIdx < 0) {
            Log.e(TAG, "no audio track in " + path);
            try { extractor.release(); } catch (Exception ignored) {}
            extractor = null;
            return false;
        }

        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        durationUs = format.containsKey(MediaFormat.KEY_DURATION)
            ? format.getLong(MediaFormat.KEY_DURATION) : 0;
        durationMs = durationUs / 1000;

        String mime = format.getString(MediaFormat.KEY_MIME);
        try {
            codec = MediaCodec.createDecoderByType(mime);
        } catch (Exception e) {
            Log.e(TAG, "createDecoderByType failed", e);
            try { extractor.release(); } catch (Exception ignored) {}
            extractor = null;
            return false;
        }

        extractor.selectTrack(audioTrackIdx);
        try {
            codec.configure(format, null, null, 0);
            codec.start();
        } catch (Exception e) {
            Log.e(TAG, "codec configure/start failed", e);
            releaseInternal();
            return false;
        }

        // 构造 AudioTrack
        int channelMask = channelCount == 1
            ? AudioFormat.CHANNEL_OUT_MONO
            : AudioFormat.CHANNEL_OUT_STEREO;
        int minBuf = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
        try {
            audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build())
                .setBufferSizeInBytes(Math.max(minBuf * 2, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        } catch (Exception e) {
            Log.e(TAG, "AudioTrack build failed", e);
            releaseInternal();
            return false;
        }

        // 后台播放线程
        audioThread = new HandlerThread("audio-playback", android.os.Process.THREAD_PRIORITY_AUDIO);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        seekOffsetMs = 0;
        isReleased = false;
        seekRequested = false;
        sawOutputEOS = false;
        Log.d(TAG, "loaded: " + sampleRate + "Hz " + channelCount + "ch " + durationMs + "ms");
        return true;
    }

    /** 开始播放 */
    public void play() {
        if (isReleased || audioTrack == null) return;
        if (isPlaying) return;

        try { audioTrack.play(); } catch (Exception e) {
            Log.e(TAG, "audioTrack.play failed", e);
            return;
        }
        isPlaying = true;
        sawOutputEOS = false;
        postDecodeLoop();
    }

    /** 暂停 */
    public void pause() {
        if (!isPlaying || audioTrack == null) return;
        isPlaying = false;
        try { audioTrack.pause(); } catch (Exception ignored) {}
        // 清空 Handler 队列中待执行的 decode 任务
        if (audioHandler != null) audioHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 跳转到指定位置（毫秒）。
     * 只设置标志位和目标位置，实际的 codec.flush 在 decodeLoop 线程内执行，
     * 避免与解码线程并发访问 MediaCodec 导致 IllegalStateException。
     */
    public void seek(long ms) {
        if (isReleased || audioTrack == null || extractor == null || codec == null) return;
        if (ms < 0) ms = 0;
        if (durationMs > 0 && ms > durationMs) ms = durationMs;

        seekTargetMs = ms;
        seekRequested = true;
        sawOutputEOS = false;
        // 暂停 AudioTrack 输出，避免 flush 后继续播放旧缓冲
        try { audioTrack.pause(); } catch (Exception ignored) {}
        // 确保 decodeLoop 在运行以处理 seek（若已因 EOS 退出则重启）
        if (audioHandler != null) {
            audioHandler.removeCallbacksAndMessages(null);
            postDecodeLoop();
        }
    }

    /** 当前位置（毫秒），采样级精度 */
    public long getPositionMs() {
        if (audioTrack == null || sampleRate <= 0) return 0;
        long headPos = audioTrack.getPlaybackHeadPosition();
        // headPosition 在 flush/stop 后归零，seekOffsetMs 补偿
        return seekOffsetMs + (headPos * 1000 / sampleRate);
    }

    /** 总时长（毫秒） */
    public long getDurationMs() {
        return durationMs;
    }

    /** 是否正在播放 */
    public boolean isPlaying() {
        return isPlaying && !isReleased;
    }

    /** 释放所有资源 */
    public void release() {
        isReleased = true;
        isPlaying = false;
        if (audioHandler != null) audioHandler.removeCallbacksAndMessages(null);
        releaseInternal();
    }

    // ── 内部方法 ──

    private void releaseInternal() {
        if (audioTrack != null) {
            try { audioTrack.stop(); } catch (Exception ignored) {}
            try { audioTrack.release(); } catch (Exception ignored) {}
            audioTrack = null;
        }
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            codec = null;
        }
        if (extractor != null) {
            try { extractor.release(); } catch (Exception ignored) {}
            extractor = null;
        }
        if (audioThread != null) {
            try { audioThread.quitSafely(); } catch (Exception ignored) {}
            audioThread = null;
            audioHandler = null;
        }
    }

    /** 投递解码-写入循环到后台线程 */
    private void postDecodeLoop() {
        if (audioHandler == null || isReleased) return;
        audioHandler.post(this::decodeLoop);
    }

    /** 解码→写入 循环（运行在 HandlerThread 上） */
    private void decodeLoop() {
        if (isReleased || audioTrack == null || codec == null || extractor == null) return;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        final long TIMEOUT_US = 10000;
        int idleCount = 0;
        final int MAX_IDLE = 300;

        while (!isReleased && !sawOutputEOS) {
            // 在线程内安全处理 seek 请求（codec 只在本线程访问，避免并发崩溃）
            if (seekRequested) {
                seekRequested = false;
                try {
                    audioTrack.pause();
                    audioTrack.flush();
                    codec.flush();
                    extractor.seekTo(seekTargetMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                } catch (Exception e) {
                    Log.e(TAG, "seek failed", e);
                }
                seekOffsetMs = seekTargetMs;
                sawInputEOS = false;
                idleCount = 0;
                if (isPlaying) {
                    try { audioTrack.play(); } catch (Exception ignored) {}
                }
                continue;
            }

            boolean didWork = false;

            // 喂压缩数据给 codec
            if (!sawInputEOS) {
                int inputBufIdx = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufIdx >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(inputBufIdx);
                    if (ib != null) {
                        int size = extractor.readSampleData(ib, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputBufIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            codec.queueInputBuffer(inputBufIdx, 0, size,
                                extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        didWork = true;
                    }
                }
            }

            // 取解码后的 PCM 写入 AudioTrack
            int outBufIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outBufIdx >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(outBufIdx);
                if (info.size > 0 && ob != null) {
                    audioTrack.write(ob, info.size, AudioTrack.WRITE_BLOCKING);
                }
                codec.releaseOutputBuffer(outBufIdx, false);
                didWork = true;

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 无输出，继续等
            } else if (outBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // PCM 格式通常不变，忽略
            }

            if (!didWork) {
                idleCount++;
                if (idleCount > MAX_IDLE) {
                    Log.e(TAG, "decodeLoop timeout, aborting");
                    break;
                }
            } else {
                idleCount = 0;
            }
        }

        // 播放完毕
        if (sawOutputEOS && !seekRequested && !isReleased) {
            isPlaying = false;
        }
    }
}
