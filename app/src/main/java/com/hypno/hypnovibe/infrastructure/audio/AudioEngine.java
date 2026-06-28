package com.hypno.hypnovibe.infrastructure.audio;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import java.nio.ByteBuffer;

/**
 * 音频引擎：Java 层用 MediaCodec 解码任意音频格式 → float[] PCM，
 * Native 层用 Oboe 低延迟播放。
 *
 * 所有 native 方法第一个参数为 ptr（句柄），不要改签名顺序。
 */
public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private long nativePtr;

    static { System.loadLibrary("hypnovibe_audio"); }

    // ── Native 方法（签名不可改）──
    private native long   nativeInit();
    private native boolean nativeLoadPcm(long ptr, float[] pcm, int sampleRate, int channels, long totalFrames);
    private native void   nativePlay(long ptr);
    private native void   nativePause(long ptr);
    private native void   nativeSeek(long ptr, long ms);
    private native long   nativeGetPosition(long ptr);
    private native long   nativeGetDuration(long ptr);
    private native boolean nativeIsPlaying(long ptr);
    private native void   nativeRelease(long ptr);

    // ── 解码结果 ──
    public static class DecodedAudio {
        public final float[] pcm;       // interleaved float PCM
        public final int sampleRate;
        public final int channels;
        public final long totalFrames;  // 帧数（不含通道）
        public final long durationMs;

        DecodedAudio(float[] pcm, int sr, int ch, long frames) {
            this.pcm = pcm; this.sampleRate = sr; this.channels = ch;
            this.totalFrames = frames;
            this.durationMs = sr > 0 ? (frames * 1000 / sr) : 0;
        }
    }

    private AudioEngine(long ptr) { this.nativePtr = ptr; }

    /** 创建引擎实例 */
    public static AudioEngine create() {
        long ptr = 0;
        try {
            AudioEngine dummy = new AudioEngine(0);
            ptr = dummy.nativeInit();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "native lib not loaded", e);
        }
        return new AudioEngine(ptr);
    }

    /**
     * 解码音频为 float[] PCM。支持本地文件路径和 content:// URI。
     * 同步执行，应在后台线程调用。
     */
    public static DecodedAudio decode(Context ctx, String path) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            if (path.startsWith("content://")) {
                // content:// URI 需要通过 fd 打开
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
            return null;
        }

        int audioTrack = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrack = i;
                format = f;
                break;
            }
        }
        if (audioTrack < 0) {
            Log.e(TAG, "no audio track in " + path);
            try { extractor.release(); } catch (Exception ignored) {}
            return null;
        }

        String mime = format.getString(MediaFormat.KEY_MIME);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        MediaCodec codec;
        try {
            codec = MediaCodec.createDecoderByType(mime);
        } catch (Exception e) {
            Log.e(TAG, "createDecoderByType failed", e);
            try { extractor.release(); } catch (Exception ignored) {}
            return null;
        }

        extractor.selectTrack(audioTrack);
        try {
            codec.configure(format, null, null, 0);
            codec.start();
        } catch (Exception e) {
            Log.e(TAG, "codec configure/start failed", e);
            try { codec.release(); } catch (Exception ignored) {}
            try { extractor.release(); } catch (Exception ignored) {}
            return null;
        }

        java.util.ArrayList<short[]> chunks = new java.util.ArrayList<>();
        int totalShorts = 0;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        final long TIMEOUT_US = 10000;
        // 防止死循环的安全计数
        int idleCount = 0;
        final int MAX_IDLE = 500;

        while (!sawOutputEOS) {
            boolean didWork = false;

            if (!sawInputEOS) {
                int inputBufIdx = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufIdx >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(inputBufIdx);
                    int sampleSize = extractor.readSampleData(ib, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufIdx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        codec.queueInputBuffer(inputBufIdx, 0, sampleSize,
                            extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                    didWork = true;
                }
            }

            int outputBufIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outputBufIdx >= 0) {
                ByteBuffer ob = codec.getOutputBuffer(outputBufIdx);
                if (info.size > 0 && ob != null) {
                    int numShorts = info.size / 2;
                    short[] chunk = new short[numShorts];
                    ob.position(info.offset);
                    ob.asShortBuffer().get(chunk);
                    chunks.add(chunk);
                    totalShorts += numShorts;
                }
                codec.releaseOutputBuffer(outputBufIdx, false);
                didWork = true;

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outputBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 超时，继续等
            }

            if (!didWork) {
                idleCount++;
                if (idleCount > MAX_IDLE) {
                    Log.e(TAG, "decode timeout, aborting");
                    break;
                }
            } else {
                idleCount = 0;
            }
        }

        try { codec.stop(); } catch (Exception ignored) {}
        try { codec.release(); } catch (Exception ignored) {}
        try { extractor.release(); } catch (Exception ignored) {}

        if (totalShorts == 0) {
            Log.e(TAG, "decoded 0 samples from " + path);
            return null;
        }

        long totalFrames = totalShorts / channels;
        float[] pcm = new float[totalShorts];
        int pos = 0;
        for (short[] chunk : chunks) {
            for (int i = 0; i < chunk.length; i++) {
                pcm[pos++] = chunk[i] / 32768.0f;
            }
        }

        Log.d(TAG, "decoded: " + totalShorts + " shorts, " + totalFrames +
              " frames, " + channels + "ch, " + sampleRate + "Hz");
        return new DecodedAudio(pcm, sampleRate, channels, totalFrames);
    }

    /** 加载已解码的 PCM 数据到 native 引擎 */
    public boolean loadDecoded(DecodedAudio audio) {
        if (nativePtr == 0) return false;
        return nativeLoadPcm(nativePtr, audio.pcm, audio.sampleRate,
                             audio.channels, audio.totalFrames);
    }

    public void play() {
        if (nativePtr != 0) nativePlay(nativePtr);
    }

    public void pause() {
        if (nativePtr != 0) nativePause(nativePtr);
    }

    public void seek(long ms) {
        if (nativePtr != 0) nativeSeek(nativePtr, ms);
    }

    public long getPosition() {
        return nativePtr != 0 ? nativeGetPosition(nativePtr) : 0;
    }

    public long getDuration() {
        return nativePtr != 0 ? nativeGetDuration(nativePtr) : 0;
    }

    public boolean isPlaying() {
        return nativePtr != 0 && nativeIsPlaying(nativePtr);
    }

    public void release() {
        if (nativePtr != 0) {
            nativeRelease(nativePtr);
            nativePtr = 0;
        }
    }
}
