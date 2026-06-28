# Phase 3 详细设计：音频引擎 (Native C)

> 阶段目标：实现基于 Oboe 的自研音频播放器，支持 MP3/WAV 解码、精确 seek 和毫秒级位置回调  
> 产出物：可运行 APK，播放列表中点击播放即可听到音频，拖拽进度条可跳转

---

## 目录

1. [架构概览](#1-架构概览)
2. [Native C 层设计](#2-native-c-层设计)
3. [JNI 桥接层](#3-jni-桥接层)
4. [Java 封装层](#4-java-封装层)
5. [UI 集成](#5-ui-集成)
6. [构建配置](#6-构建配置)
7. [文件清单](#7-文件清单)
8. [验证标准](#8-验证标准)

---

## 1. 架构概览

```
┌────────────────────────────────────────────┐
│  Kotlin UI (Compose)                        │
│  PlaylistDetailScreen → PlayerBar           │
│             ↕ StateFlow                     │
│  Java ViewModel                             │
│  PlaySessionVM ←→ AudioEngine (JNI wrapper) │
├────────────────────────────────────────────┤
│  JNI Bridge                                 │
│  AudioEngine.java → jni_bridge.c            │
├────────────────────────────────────────────┤
│  Native C (Oboe)                            │
│  audio_engine.c — 播放控制                  │
│  mp3_decoder.c — MP3→PCM                   │
│  wav_decoder.c  — WAV→PCM                  │
│  waveform_sampler.c — PCM→渲染数据点        │
└────────────────────────────────────────────┘
```

---

## 2. Native C 层设计

### 2.1 minimp3 集成

[minimp3](https://github.com/lieff/minimp3) 是单头文件 MP3 解码器，无需编译链接。

```
app/src/main/cpp/
├── minimp3/
│   └── minimp3.h          # 从 GitHub 下载，放入 cpp/minimp3/
├── mp3_decoder.h
└── mp3_decoder.c
```

```c
// mp3_decoder.h
typedef struct {
    mp3dec_t mp3d;
    mp3dec_file_info_t info;
    int16_t* pcm_buffer;   // 解码后的 PCM 16-bit buffer
    size_t   pcm_size;     // 采样数
    int      sample_rate;
    int      channels;
    int      is_loaded;
} Mp3Decoder;

int   mp3_load_file(Mp3Decoder* dec, const char* path);
int   mp3_get_samples(Mp3Decoder* dec, float* out, int offset, int count);
void  mp3_release(Mp3Decoder* dec);
```

- `mp3_load_file`：调用 `mp3dec_load()` 一次性解码整个文件到内存
- `mp3_get_samples`：从 PCM buffer 指定偏移读取指定数量采样点，转为 float [-1,1]

### 2.2 WAV 解码

```c
// wav_decoder.h
typedef struct {
    int16_t* pcm_buffer;
    size_t   pcm_size;
    int      sample_rate;
    int      channels;
    int      bit_depth;
    int      is_loaded;
} WavDecoder;

int   wav_load_file(WavDecoder* dec, const char* path);
int   wav_get_samples(WavDecoder* dec, float* out, int offset, int count);
void  wav_release(WavDecoder* dec);
```

解析流程：RIFF chunk → fmt chunk（获取 sample_rate/channels/bit_depth）→ data chunk（读取 PCM）

### 2.3 音频引擎 (Oboe) — 多声道 + 精确控制

**PCM 数据布局**：`float pcm[total_frames * channels]`，交错存储。例如立体声 `[L0, R0, L1, R1, ...]`。

**帧 vs 采样点**：帧(frame) = 同一时刻所有声道的采样点。`total_frames` 和 `current_frame` 都以帧为单位。`engine_get_position_ms()` = `current_frame * 1000 / sample_rate`。

```c
// audio_engine.h
typedef struct {
    // Oboe
    oboe::AudioStream* stream;
    oboe::AudioStreamBuilder builder;

    // 解码器（二选一）
    int is_mp3;
    Mp3Decoder mp3;
    WavDecoder wav;

    // 播放状态
    volatile int is_playing;       // 原子标记，Oboe 回调 + Java 线程读写
    volatile int seek_requested;   // seek 标记
    volatile long seek_target_ms;  // 目标位置
    int      is_completed;
    int      total_frames;
    int      current_frame;        // 当前帧位置（仅 Oboe 回调线程写入）
    int      sample_rate;
    int      channels;

    // PCM 数据（交错 float）
    float*   pcm_float;            // total_frames * channels 个 float

    // 回调函数指针（JNI 设置）
    void (*on_position_update)(long position_ms);
    void (*on_playback_complete)();
} AudioEngine;

int  engine_init(AudioEngine* eng);
int  engine_load_file(AudioEngine* eng, const char* path);
// sample_rate 和 channels 由解码器填充，调用方从 eng 读取
void engine_play(AudioEngine* eng);
void engine_pause(AudioEngine* eng);
void engine_seek(AudioEngine* eng, long position_ms);  // 原子设置 seek 标记
long engine_get_position_ms(AudioEngine* eng);
long engine_get_duration_ms(AudioEngine* eng);
int  engine_is_playing(AudioEngine* eng);
void engine_release(AudioEngine* eng);
```

**Oboe 回调（完整版，支持 seek + 多声道 + 精确位置）**：

```c
oboe::DataCallbackResult onAudioReady(
    oboe::AudioStream* stream, void* audioData, int32_t numFrames) {

    AudioEngine* eng = (AudioEngine*)stream->getContext();
    float* out = (float*)audioData;

    // --- 处理 seek 请求 ---
    if (eng->seek_requested) {
        long target_ms = eng->seek_target_ms;
        eng->current_frame = (int)((target_ms * eng->sample_rate) / 1000);
        if (eng->current_frame >= eng->total_frames)
            eng->current_frame = eng->total_frames;
        eng->seek_requested = 0;
        // seek 后立即通知 Java 层新位置
        if (eng->on_position_update)
            eng->on_position_update(eng->current_frame * 1000 / eng->sample_rate);
    }

    // --- 填充音频数据 ---
    int remaining = eng->total_frames - eng->current_frame;
    int to_copy = (numFrames < remaining) ? numFrames : remaining;

    if (to_copy > 0) {
        // 交错 PCM：一次性拷贝 to_copy * channels 个 float
        memcpy(out,
               eng->pcm_float + eng->current_frame * eng->channels,
               to_copy * eng->channels * sizeof(float));
        eng->current_frame += to_copy;

        // 尾部填充静音
        if (to_copy < numFrames) {
            memset(out + to_copy * eng->channels, 0,
                   (numFrames - to_copy) * eng->channels * sizeof(float));
        }
    } else {
        memset(out, 0, numFrames * eng->channels * sizeof(float));
        eng->is_completed = 1;
        eng->is_playing = 0;
        if (eng->on_playback_complete) eng->on_playback_complete();
        return oboe::DataCallbackResult::Stop;
    }

    return oboe::DataCallbackResult::Continue;
}
```

**Oboe Stream 构建参数**：

```c
void engine_create_stream(AudioEngine* eng) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           .setPerformanceMode(oboe::PerformanceMode::LowLatency)
           .setSharingMode(oboe::SharingMode::Exclusive)
           .setFormat(oboe::AudioFormat::Float)
           .setChannelCount(eng->channels)          // 使用实际声道数
           .setSampleRate(eng->sample_rate)
           .setDataCallback(&onAudioReady)
           .setContext(eng);
    builder.openStream(&eng->stream);
}
```

> `setChannelCount(eng->channels)` 保证立体声正确推送到耳机左右声道。

**声道兼容策略**（与常规播放软件一致）：

| 音频文件 | 设备输出 | 处理方式 |
|------|------|------|
| 单声道 | 立体声耳机 | Oboe 自动将单声道复制到左右双声道（标准行为） |
| 立体声 | 立体声耳机 | 左→左，右→右，无处理 |
| 多声道(5.1) | 立体声耳机 | Oboe 自动下混为立体声（标准 downmix） |
| 任意 | 任意 | **不自行做声道变换**，全部交由 Oboe 处理，行为与系统播放器一致 |

**采样率**：`engine_init()` 中不预设采样率，由解码器从文件头读取后设置。Oboe 首选设备原生采样率（`AudioStreamBuilder::openStream` 自动协商），避免 Android 系统重采样引入延迟。

**播放控制接口（完整列表）**：

| 方法 | 线程安全 | 说明 |
|------|:--:|------|
| `engine_play()` | 任意线程 | 打开 stream → requestStart |
| `engine_pause()` | 任意线程 | requestPause + requestStop（必须等待 stopped 再返回） |
| `engine_seek(ms)` | 任意线程 | 原子设置 seek_requested=1 + seek_target_ms，下次回调生效 |
| `engine_get_position_ms()` | 任意线程 | `current_frame * 1000 / sample_rate` |
| `engine_get_duration_ms()` | 任意线程 | `total_frames * 1000 / sample_rate` |
| `engine_is_playing()` | 任意线程 | 读 volatile is_playing |

**切换曲目流程**：

```
用户点下一首:
  Java: vm.playNext()
    → engine_pause()           // 暂停当前
    → engine_release()         // 释放旧引擎
    → engine = new AudioEngine()
    → engine_load_file(nextPath)  // 加载新文件
    → engine_play()               // 开始播放
    → 更新 currentTrackIndex
```

> Phase 6 将优化为预加载池（提前加载下一首的 PCM 到内存），减少切换延迟。Phase 3 使用最简单的一次性加载方案。

### 2.4 波形采样

```c
// waveform_sampler.c
// 将 PCM 数据降采样为渲染用的数据点
// 例如：44100 采样点/秒 → 每个像素 1 个数据点（peak + RMS）

void waveform_sample(float* pcm, int total_frames, int channels,
                     float* out_peaks, int out_count);
```

输出 `out_peaks[out_count * 2]`（每点两个值：min 和 max，用于波形图绘制）

---

## 3. JNI 桥接层

### 3.1 Java 声明

```java
public class AudioEngine {
    static { System.loadLibrary("hypnovibe_audio"); }

    public native long nativeInit(int sampleRate, int channels);
    public native boolean nativeLoadFile(long ptr, String path);
    public native void nativePlay(long ptr);
    public native void nativePause(long ptr);
    public native void nativeSeek(long ptr, long positionMs);
    public native long nativeGetPosition(long ptr);
    public native long nativeGetDuration(long ptr);
    public native boolean nativeIsPlaying(long ptr);
    public native void nativeRelease(long ptr);

    // 回调注册
    public native void nativeSetPositionUpdateCallback(long ptr, AudioEngine engine);
    public native void nativeSetCompletionCallback(long ptr, AudioEngine engine);
}
```

### 3.2 C 层 JNI 实现

```c
// jni_bridge.c
JNIEXPORT jlong JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeInit(
    JNIEnv* env, jclass clazz, jint sampleRate, jint channels) {
    AudioEngine* eng = (AudioEngine*)malloc(sizeof(AudioEngine));
    engine_init(eng, sampleRate, channels);
    return (jlong)eng;
}
// ... 其他 JNI 函数类似
```

---

## 4. Java 封装层

### 4.1 AudioEngine.java

```java
public class AudioEngine {
    private long nativePtr;
    private AudioPlayerCallback callback;

    public interface AudioPlayerCallback {
        void onPositionUpdate(long positionMs);
        void onPlaybackComplete();
    }

    public void setCallback(AudioPlayerCallback cb) { this.callback = cb; }

    public boolean loadFile(String path) {
        if (nativePtr == 0) return false;
        String realPath = resolveContentUri(path);
        return nativeLoadFile(nativePtr, realPath);
    }

    public void play() { nativePlay(nativePtr); }
    public void pause() { nativePause(nativePtr); }
    public void seek(long ms) { nativeSeek(nativePtr, ms); }
    public long getPosition() { return nativeGetPosition(nativePtr); }
    public long getDuration() { return nativeGetDuration(nativePtr); }
    public boolean isPlaying() { return nativeIsPlaying(nativePtr); }

    public void release() {
        if (nativePtr != 0) { nativeRelease(nativePtr); nativePtr = 0; }
    }

    // 从 Java 线程回调 C 层触发的位置更新
    private void onNativePositionUpdate(long ms) {
        if (callback != null) callback.onPositionUpdate(ms);
    }
    private void onNativePlaybackComplete() {
        if (callback != null) callback.onPlaybackComplete();
    }
}
```

### 4.2 PlaySessionVM 扩展

```java
private AudioEngine audioEngine;
private int currentTrackIndex = -1;
private ScheduledExecutorService positionPoller;  // Phase 3 位置轮询

private final MutableStateFlow<Long> positionMs = new MutableStateFlow<>(0L);
private final MutableStateFlow<Long> durationMs = new MutableStateFlow<>(0L);
private final MutableStateFlow<Boolean> isPlaying = new MutableStateFlow<>(false);

public StateFlow<Long> getPositionMs() { return positionMs; }
public StateFlow<Long> getDurationMs() { return durationMs; }
public StateFlow<Boolean> getIsPlaying() { return isPlaying; }

public void initAudioEngine() {
    audioEngine = new AudioEngine();
    audioEngine.nativeInit(0, 0);
    audioEngine.setCallback(new AudioEngine.AudioPlayerCallback() {
        @Override public void onPositionUpdate(long ms) { positionMs.setValue(ms); }
        @Override public void onPlaybackComplete() {
            isPlaying.setValue(false);
            if (playMode().equals("LOOP_LIST") || playMode().equals("LOOP_LAST"))
                playNext();
        }
    });
    // 每 ~33ms 轮询位置，供 UI 进度条 + 时间轴引擎同步
    positionPoller = Executors.newSingleThreadScheduledExecutor();
    positionPoller.scheduleAtFixedRate(() -> {
        positionMs.setValue(audioEngine.getPosition());
    }, 0, 33, TimeUnit.MILLISECONDS);
}

public void playTrack(int index) { /* 释放旧引擎 → 加载新文件 → 播放 */ }
public void togglePlayPause() { /* 切换播放/暂停 */ }
public void seek(long ms) { /* seek + Phase 6 通知 Coordinator.flush() */ }
public void playNext() { /* 根据 playMode 计算下一首索引 */ }
public void playPrevious() { /* 上一首或归零 */ }

---

## 5. UI 集成

### 5.1 PlaylistDetailScreen 的 PlayerBar

PlayerBar 从纯装饰变为功能版本：

- 播放按钮：调用 `vm.togglePlayPause()`
- 进度条：`vm.getPositionMs()` → 进度条显示 + 可拖动
- 上/下一首：`vm.playNext()` / `vm.playPrevious()`

### 5.2 进度条实现

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

## 6. 构建配置

### 6.1 CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("hypnovibe_audio")

# minimp3 (header-only)
include_directories(${CMAKE_SOURCE_DIR}/src/main/cpp/minimp3)

add_library(hypnovibe_audio SHARED
    audio_engine.c
    mp3_decoder.c
    wav_decoder.c
    waveform_sampler.c
    jni_bridge.c
)

# Oboe
find_library(oboe oboe)
target_link_libraries(hypnovibe_audio ${oboe} log)
```

### 6.2 minimp3 下载

手动从 https://raw.githubusercontent.com/lieff/minimp3/master/minimp3.h 下载，放入 `app/src/main/cpp/minimp3/minimp3.h`

---

## 7. 文件清单

### 新增/修改

```
app/src/main/cpp/
├── CMakeLists.txt          (重写，添加实际源文件)
├── minimp3/
│   └── minimp3.h           (下载)
├── audio_engine.h
├── audio_engine.c
├── mp3_decoder.h
├── mp3_decoder.c
├── wav_decoder.h
├── wav_decoder.c
├── waveform_sampler.h
├── waveform_sampler.c
└── jni_bridge.c

app/src/main/java/.../
├── infrastructure/audio/
│   └── AudioEngine.java   (新增或重写)
└── app/viewmodel/
    └── PlaySessionVM.java  (扩展，添加播放控制)

app/src/main/kotlin/.../ui/screen/playlist/
└── PlaylistDetailScreen.kt (PlayerBar 接入真实播放)
```

---

## 8. 验证标准

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
