package com.hypno.hypnovibe.infrastructure.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 纯 Java 音频引擎：MediaExtractor + MediaCodec 流式解码 → AudioTrack 播放。
 *
 * 设计要点：
 *  - 流式解码：内存占用 KB 级，不需要全量加载 PCM
 *  - 进度精确：AudioTrack.getPlaybackHeadPosition() 采样级精度，配合 seekOffset 计算毫秒位置
 *  - seek 受控：extractor.seekTo + flush codec/audioTrack，精确跳转
 *  - 多声道：AudioTrack 使用源文件实际声道配置，系统框架自动 downmix 到输出设备
 */
public class AudioEngine {
    private static final String TAG = "AudioEngine";

    private MediaExtractor extractor;
    private MediaCodec codec;
    private AudioTrack audioTrack;
    private Thread decodeThread;

    private int sampleRate;
    private int channelCount;
    private long durationMs;
    /** seek/flush 后补偿 headPosition 归零 */
    private volatile long seekOffsetMs;

    /** 播放状态标志，控制解码线程是否向 AudioTrack 喂数据 */
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isReleased = new AtomicBoolean(false);
    /** 解码线程是否应该继续运行 */
    private final AtomicBoolean decodeRunning = new AtomicBoolean(false);
    /** seek 请求标志，解码线程检测后执行 */
    private final AtomicBoolean seekRequested = new AtomicBoolean(false);
    private volatile long seekTargetMs = 0;

    /** 暂停时阻塞解码线程 */
    private final Object pauseLock = new Object();
    /** 保护 extractor/codec（解码线程 + 主线程 seek 都会访问） */
    private final Object codecLock = new Object();

    /** 播放完成监听 */
    public interface CompletionListener {
        void onPlaybackComplete();
    }
    private volatile CompletionListener completionListener;
    private volatile boolean completedFired = false;

    public AudioEngine() {}

    public void setCompletionListener(CompletionListener l) {
        this.completionListener = l;
    }

    // ── 加载文件 ──────────────────────────────────────────

    /**
     * 加载音频文件，自动探测格式并构造解码器与 AudioTrack。
     * 支持本地文件路径和 content:// URI。
     * @return 成功返回 true
     */
    public boolean loadFile(Context ctx, String path) {
        if (isReleased.get()) return false;
        if (path == null || path.isEmpty()) return false;

        try {
            extractor = new MediaExtractor();
            if (path.startsWith("content://")) {
                extractor.setDataSource(ctx, Uri.parse(path), null);
            } else {
                extractor.setDataSource(path);
            }
        } catch (Exception e) {
            Log.e(TAG, "setDataSource failed: " + path, e);
            releaseInternal();
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
        if (audioTrackIdx < 0 || format == null) {
            Log.e(TAG, "no audio track in " + path);
            releaseInternal();
            return false;
        }

        extractor.selectTrack(audioTrackIdx);
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000;
        }

        // 创建解码器
        String mime = format.getString(MediaFormat.KEY_MIME);
        try {
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
        } catch (Exception e) {
            Log.e(TAG, "codec init failed", e);
            releaseInternal();
            return false;
        }

        // 创建 AudioTrack，使用源声道配置让系统正确 downmix
        if (!createAudioTrack()) {
            releaseInternal();
            return false;
        }

        seekOffsetMs = 0;
        completedFired = false;
        Log.d(TAG, "loaded: " + channelCount + "ch, " + sampleRate + "Hz, " + durationMs + "ms");
        return true;
    }

    /** 根据源声道数构造 AudioTrack，不支持时降级到立体声 */
    private boolean createAudioTrack() {
        int channelMask = channelCountToOutMask(channelCount);
        int minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize failed for mask=" + channelMask);
            return false;
        }

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
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                    .build();
        } catch (Exception e) {
            Log.w(TAG, "AudioTrack build with mask=" + channelMask + " failed, fallback to STEREO", e);
            // 降级：源是多声道但设备不支持时，强制立体声（系统重采样）
            try {
                channelMask = AudioFormat.CHANNEL_OUT_STEREO;
                minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask,
                        AudioFormat.ENCODING_PCM_16BIT);
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
                        .setBufferSizeInBytes(minBuf * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } catch (Exception e2) {
                Log.e(TAG, "AudioTrack fallback failed", e2);
                return false;
            }
        }
        return true;
    }

    private static int channelCountToOutMask(int count) {
        switch (count) {
            case 1: return AudioFormat.CHANNEL_OUT_MONO;
            case 2: return AudioFormat.CHANNEL_OUT_STEREO;
            case 4: return AudioFormat.CHANNEL_OUT_QUAD;
            case 6: return AudioFormat.CHANNEL_OUT_5POINT1;
            case 8: return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
            default: return AudioFormat.CHANNEL_OUT_STEREO;
        }
    }

    // ── 播放控制 ──────────────────────────────────────────

    /** 开始播放（首次或从暂停恢复） */
    public void play() {
        if (isReleased.get() || audioTrack == null) return;
        completedFired = false;
        isPlaying.set(true);
        audioTrack.play();
        // 启动或重启解码线程
        if (decodeThread == null || !decodeThread.isAlive()) {
            startDecodeLoop();
        }
        // 唤醒可能被 pause 阻塞的解码线程
        synchronized (pauseLock) { pauseLock.notifyAll(); }
    }

    /** 暂停播放 */
    public void pause() {
        if (isReleased.get() || audioTrack == null) return;
        isPlaying.set(false);
        try { audioTrack.pause(); } catch (Exception ignored) {}
    }

    /**
     * 精确跳转到指定毫秒位置。
     * 实际 seek 操作在解码线程内执行（避免与解码并发访问 codec）。
     */
    public void seek(long ms) {
        if (isReleased.get() || extractor == null) return;
        if (ms < 0) ms = 0;
        if (durationMs > 0 && ms > durationMs) ms = durationMs;
        completedFired = false;
        seekTargetMs = ms;
        seekRequested.set(true);
        // 唤醒可能被 pause 阻塞的解码线程以处理 seek
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        // 若解码线程已退出（播放结束），重启它执行 seek
        if (decodeThread == null || !decodeThread.isAlive()) {
            startDecodeLoop();
        }
    }

    // ── 状态查询 ──────────────────────────────────────────

    /** 当前播放位置（毫秒），采样级精度 */
    public long getPositionMs() {
        if (audioTrack == null || sampleRate <= 0) return seekOffsetMs;
        long headPos = audioTrack.getPlaybackHeadPosition();
        return seekOffsetMs + (headPos * 1000 / sampleRate);
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isPlaying() {
        return isPlaying.get() && !isReleased.get();
    }

    // ── 解码线程 ──────────────────────────────────────────

    private void startDecodeLoop() {
        decodeRunning.set(true);
        decodeThread = new Thread(this::decodeLoop, "audio-decode");
        decodeThread.setPriority(Thread.MAX_PRIORITY);
        decodeThread.start();
    }

    private void decodeLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        while (decodeRunning.get() && !isReleased.get()) {
            // 处理 seek 请求（在解码线程内同步操作 codec/extractor）
            if (seekRequested.compareAndSet(true, false)) {
                sawInputEOS = false;
                sawOutputEOS = false;
                doSeekInternal();
                continue;
            }

            // 暂停时阻塞，避免空转消耗 CPU
            if (!isPlaying.get()) {
                try {
                    synchronized (pauseLock) {
                        while (!isPlaying.get() && decodeRunning.get()
                                && !seekRequested.get() && !isReleased.get()) {
                            pauseLock.wait(200);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            synchronized (codecLock) {
                if (!decodeRunning.get() || isReleased.get()) break;

                try {
                    // 喂入压缩数据
                    if (!sawInputEOS && codec != null) {
                        int inputIdx = codec.dequeueInputBuffer(10000);
                        if (inputIdx >= 0) {
                            ByteBuffer ib = codec.getInputBuffer(inputIdx);
                            int size = (ib != null) ? extractor.readSampleData(ib, 0) : -1;
                            if (size < 0) {
                                codec.queueInputBuffer(inputIdx, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                codec.queueInputBuffer(inputIdx, 0, size,
                                        extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }

                    // 取出解码 PCM 并写入 AudioTrack
                    if (codec != null && audioTrack != null) {
                        int outIdx = codec.dequeueOutputBuffer(info, 10000);
                        if (outIdx >= 0) {
                            ByteBuffer ob = codec.getOutputBuffer(outIdx);
                            if (info.size > 0 && ob != null) {
                                // 阻塞写入：缓冲区满时自动等待，天然流控
                                audioTrack.write(ob, info.size, AudioTrack.WRITE_BLOCKING);
                            }
                            codec.releaseOutputBuffer(outIdx, false);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "decode loop error", e);
                    break;
                }
            }
        }

        // 播放结束回调
        if (sawOutputEOS && !seekRequested.get() && !isReleased.get()) {
            completedFired = true;
            isPlaying.set(false);
            if (completionListener != null) {
                completionListener.onPlaybackComplete();
            }
        }
    }

    /** 在解码线程内执行 seek：flush 所有组件并跳转 extractor */
    private void doSeekInternal() {
        synchronized (codecLock) {
            if (audioTrack != null) {
                try {
                    audioTrack.pause();
                    audioTrack.flush();
                } catch (Exception ignored) {}
            }
            if (codec != null) {
                try {
                    codec.flush();
                    codec.start();
                } catch (Exception ignored) {}
            }
            try {
                extractor.seekTo(seekTargetMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            } catch (Exception ignored) {}
            seekOffsetMs = seekTargetMs;
            if (isPlaying.get() && audioTrack != null) {
                try { audioTrack.play(); } catch (Exception ignored) {}
            }
        }
    }

    // ── 释放 ──────────────────────────────────────────────

    public void release() {
        releaseInternal();
    }

    private void releaseInternal() {
        isReleased.set(true);
        decodeRunning.set(false);
        isPlaying.set(false);
        // 唤醒解码线程使其退出
        synchronized (pauseLock) { pauseLock.notifyAll(); }

        // 等待解码线程结束
        Thread t = decodeThread;
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ignored) {}
            decodeThread = null;
        }

        synchronized (codecLock) {
            if (audioTrack != null) {
                try {
                    audioTrack.stop();
                    audioTrack.flush();
                } catch (Exception ignored) {}
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
        }
    }
}
