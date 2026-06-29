# Phase 3 详细设计：音频引擎 (纯 Java AudioTrack + MediaCodec)

> 阶段目标：实现基于 AudioTrack + MediaCodec 的流式音频播放器，支持任意音频格式、精确 seek 和毫秒级位置查询  
> 产出物：可运行 APK，播放列表中点击播放即可听到音频，拖拽进度条可跳转

---

## 目录

1. [架构概览](#1-架构概览)
2. [流式解码播放设计](#2-流式解码播放设计)
3. [Java AudioEngine 封装](#3-java-audioengine-封装)
4. [UI 集成](#4-ui-集成)
5. [构建配置](#5-构建配置)
6. [文件清单](#6-文件清单)
7. [验证标准](#7-验证标准)

---

## 1. 架构概览

```
┌────────────────────────────────────────────┐
│  Kotlin UI (Compose)                        │
│  PlaylistDetailScreen → PlayerBar           │
│             ↕ StateFlow                     │
│  Java ViewModel                             │
│  PlaySessionVM ←→ AudioEngine (纯 Java)      │
├────────────────────────────────────────────┤
│  Java AudioEngine                           │
│  MediaExtractor — 流式读取文件               │
│  MediaCodec — 流式解码到 short[] PCM         │
│  AudioTrack — 播放、进度查询、seek            │
│  (全部运行在后台 HandlerThread)               │
└────────────────────────────────────────────┘
```

---

## 2. 流式解码播放设计

### 2.1 核心原理

```
MediaExtractor(读文件块) → MediaCodec(解码) → AudioTrack.write(播放)
        ↑ seek: seekTo + flush                    ↑ getPlaybackHeadPosition
```

- **流式**：不一次性加载全部 PCM 到内存。每轮解码 1 块，写入 AudioTrack，循环直到文件结束。内存占用 KB 级
- **阻塞流控**：`AudioTrack.write()` 在缓冲区满时自动阻塞，天然流量控制，无需 `sleep` 轮询
- **进度**：`AudioTrack.getPlaybackHeadPosition()` 返回已渲染采样数。`realPosMs = seekOffsetMs + headPosition * 1000 / sampleRate`。精度到采样级（~0.02ms @ 48kHz）
- **多声道**：`AudioTrack` 构造时使用源文件实际 `channelCount`，Android 音频框架自动 downmix 到输出设备，行为与系统播放器一致

### 2.2 AudioTrack 构造

```java
int bufferSize = AudioTrack.getMinBufferSize(
    sampleRate,                                  // 源文件采样率
    channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
    AudioFormat.ENCODING_PCM_16BIT
);

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
    .setBufferSizeInBytes(bufferSize * 2)
    .setTransferMode(AudioTrack.MODE_STREAM)
    .build();
```

> `MODE_STREAM` + `write()` 阻塞行为保证播放线程与音频硬件同步，是流式播放的基础。

### 2.3 播放线程

后台 `HandlerThread("audio-playback")` 中运行解码-写入循环：

```java
// 伪代码
HandlerThread audioThread = new HandlerThread("audio-playback");
Handler handler = new Handler(audioThread.getLooper());

handler.post(() -> {
    while (!isReleased && !isEOS) {
        // 1. 从 extractor 读一帧压缩数据
        int inputBufIdx = codec.dequeueInputBuffer(10000);
        if (inputBufIdx >= 0) {
            ByteBuffer ib = codec.getInputBuffer(inputBufIdx);
            int size = extractor.readSampleData(ib, 0);
            if (size < 0) {
                codec.queueInputBuffer(inputBufIdx, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM);
                sawInputEOS = true;
            } else {
                codec.queueInputBuffer(inputBufIdx, 0, size, extractor.getSampleTime(), 0);
                extractor.advance();
            }
        }

        // 2. 取解码后的 PCM
        int outBufIdx = codec.dequeueOutputBuffer(info, 10000);
        if (outBufIdx >= 0) {
            ByteBuffer ob = codec.getOutputBuffer(outBufIdx);
            if (info.size > 0) {
                // 3. 写入 AudioTrack（满则阻塞）
                audioTrack.write(ob, info.size, AudioTrack.WRITE_BLOCKING);
                bytesWritten += info.size;
            }
            codec.releaseOutputBuffer(outBufIdx, false);
            if ((info.flags & BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true;
        }
    }
});
```

### 2.4 seek 实现

```
seek(ms):
  1. 设置 seek_requested 标志 → Handler 退出当前 write 阻塞
  2. audioTrack.pause()
  3. audioTrack.flush()        // 清空播放缓冲区
  4. codec.flush()             // 清空解码器缓冲区
  5. extractor.seekTo(ms * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
  6. seekOffsetMs = ms         // 因 flush 后 headPosition 归零，需要用 offset 补偿
  7. bytesWritten = 0
  8. audioTrack.play()
  9. 重新 post 解码循环到 Handler
```

位置计算：

```java
public long getPositionMs() {
    if (audioTrack == null || sampleRate <= 0) return 0;
    long playedFrames = audioTrack.getPlaybackHeadPosition();
    // headPosition 在 flush/stop 后归零，seekOffsetMs 补偿
    return seekOffsetMs + (playedFrames * 1000 / sampleRate);
}
```

### 2.5 pause / resume

- **pause**: `audioTrack.pause()` → 解码线程继续运行，但 `audioTrack.write()` 之后调用会被阻塞或丢弃（实际上 pause 后 write 返回 0 或等待）；更稳妥的做法是同时停止 Handler 投递
- **resume**: `audioTrack.play()` → 重新 post 解码循环

### 2.6 声道兼容

| 音频源 | 设备输出 | AudioTrack 行为 |
|------|------|------|
| 单声道 | 立体声耳机 | `CHANNEL_OUT_MONO` → 系统复制到左右 |
| 立体声 | 立体声耳机 | `CHANNEL_OUT_STEREO` → 左到左，右到右 |
| 5.1/7.1 | 立体声耳机 | 系统 downmix 为立体声 |
| 任意 | 任意 | **不自行做声道变换**，全部交由 Android 框架处理 |

---

## 3. Java AudioEngine 封装

### 3.1 AudioEngine.java API

```java
public class AudioEngine {
    // 播放控制
    public void play();
    public void pause();
    public void seek(long ms);
    public void release();
    
    // 状态查询
    public long getPositionMs();
    public long getDurationMs();
    public boolean isPlaying();
    
    // 加载文件（自动获取音频格式信息并构造 AudioTrack）
    public boolean loadFile(Context ctx, String path);
}
```

### 3.2 内部结构

```java
public class AudioEngine {
    private MediaExtractor extractor;
    private MediaCodec codec;
    private AudioTrack audioTrack;
    private HandlerThread audioThread;
    private Handler audioHandler;
    
    private int sampleRate;
    private int channelCount;
    private long durationMs;
    private long seekOffsetMs;       // seek 后补偿 headPosition 归零
    private volatile boolean isPlaying;
    private volatile boolean isReleased;
}
```

### 3.3 PlaySessionVM 使用流程

```java
// 播放曲目（后台线程）
ioExecutor.execute(() -> {
    AudioEngine engine = new AudioEngine();
    if (!engine.loadFile(ctx, audioPath)) { /* 错误 */ return; }
    engine.play();
    currentTrackIndex = index;
    durationMs = engine.getDurationMs();
    
    // 启动 50ms 位置轮询
    positionPoller.scheduleAtFixedRate(() -> {
        positionMs.setValue(engine.getPositionMs());
        if (engine.getDurationMs() > 0 
            && engine.getPositionMs() >= engine.getDurationMs() 
            && !completedFired) {
            completedFired = true;
            playNext();
        }
    }, 0, 50, TimeUnit.MILLISECONDS);
});

// toggle 播放/暂停
if (audioEngine.isPlaying()) {
    audioEngine.pause();
} else {
    audioEngine.play();
}

// seek
audioEngine.seek(targetMs);
positionMs.setValue(targetMs);

## 4. UI 集成

### 4.1 PlaylistDetailScreen 的 PlayerBar

PlayerBar 从纯装饰变为功能版本：

- 播放按钮：调用 `vm.togglePlayPause()`
- 进度条：`vm.getPositionMs()` → 进度条显示 + 可拖动
- 上/下一首：`vm.playNext()` / `vm.playPrevious()`

### 4.2 进度条实现

```kotlin
// PlayerBar 内
var seekPosition by remember { mutableFloatStateOf(0f) }
val position by vm.getPositionMs().collectAsState()
val duration by vm.getDurationMs().collectAsState()

RunicProgressBar(
    progress = if (duration > 0) position.toFloat() / duration else 0f,
)

Slider(  // 叠加在进度条上，透明 thumb
    value = if (duration > 0) position.toFloat() / duration else 0f,
    onValueChange = { vm.seek((it * duration).toLong()) },
    modifier = Modifier.height(24.dp).alpha(0f)  // 透明 Slider 来捕获触摸
)
```

---

## 5. 构建配置

音频引擎使用纯 Java `android.media` API（AudioTrack、MediaCodec、MediaExtractor），均属于 Android SDK 内置，**无需任何额外依赖或 native 编译**。

### 5.1 需要移除的依赖

| 依赖 | 说明 |
|------|------|
| `com.google.oboe:oboe` | Oboe AAR，不再需要 |
| `externalNativeBuild.cmake` | NDK/CMake 编译配置，不再需要 |
| `prefab = true` | Prefab 用于暴露 native 库，不再需要 |
| `cpp/CMakeLists.txt` | Native 编译脚本，删除 |
| `cpp/minimp3/` | MP3 解码头文件，删除 |
| `cpp/*.c / cpp/*.h` | 所有 Native 源文件，删除 |

### 5.2 保留/新增的依赖

无。`MediaCodec`、`MediaExtractor`、`AudioTrack` 均为 `android.media` 包内置 API，无需额外 Gradle 依赖。

---

## 6. 文件清单

### 新增/修改

```
app/src/main/java/.../
├── infrastructure/audio/
│   └── AudioEngine.java   (重写：纯 Java，移除所有 native 方法)
└── app/viewmodel/
    └── PlaySessionVM.java  (适配新 API)
```

### 删除

```
app/src/main/cpp/               (整个目录删除)
├── CMakeLists.txt
├── minimp3/minimp3.h
├── audio_engine.h / audio_engine.c
├── mp3_decoder.h / mp3_decoder.c
├── wav_decoder.h / wav_decoder.c
├── waveform_sampler.h / waveform_sampler.c
└── jni_bridge.c

---

## 7. 验证标准

| # | 检查项 |
|:--:|------|
| 1 | MP3 文件 → 点击播放 → 左右声道正常听到声音 |
| 2 | 立体声 WAV → 左声道和右声道分别正确输出到耳机 |
| 3 | 播放中拖动进度条 → seek 到新位置继续播放，无杂音/爆音 |
| 4 | 进度条随播放实时移动（~33ms 刷新） |
| 5 | 播放到末尾 → 自动下一首（LOOP_LIST 模式）或停止（STOP_AT_END） |
| 6 | 暂停 → 进度条不动 → 恢复 → 从暂停位置继续 |
| 7 | 上/下一首按钮可正常切换曲目 |
| 8 | 播放中途 seek 后，`positionMs` 值与实际听到的位置一致 |
| 9 | 播放一分钟后 `positionMs` 与实际时间偏差 < 50ms |
| 10 | 无内存泄漏：播放→暂停→切换曲目反复 10 次，内存稳定 |
