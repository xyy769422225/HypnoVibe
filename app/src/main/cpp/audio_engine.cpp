#include "audio_engine.h"
#include <oboe/Oboe.h>
#include <cstdlib>
#include <cstring>
#include <atomic>
#include <memory>

// ─── 引擎内部结构 ───────────────────────────────────────
struct AudioEngine {
    float* pcm = nullptr;              // interleaved float PCM
    long   total_frames = 0;
    int    sample_rate = 0;
    int    channels = 0;

    std::atomic<long>  current_frame{0};
    std::atomic<int>   is_playing{0};
    std::atomic<int>   seek_requested{0};
    std::atomic<long>  seek_target_ms{0};

    std::shared_ptr<oboe::AudioStream> stream;
};

// ─── Oboe 回调 ──────────────────────────────────────────
class EngineCallback : public oboe::AudioStreamDataCallback {
public:
    AudioEngine* eng;

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream*, void* audioData, int32_t numFrames) override {

        float* out = (float*)audioData;

        // 处理 seek 请求
        if (eng->seek_requested.exchange(0)) {
            long targetFrame = (eng->seek_target_ms * eng->sample_rate) / 1000;
            if (targetFrame < 0) targetFrame = 0;
            if (targetFrame > eng->total_frames) targetFrame = eng->total_frames;
            eng->current_frame = targetFrame;
        }

        long remaining = eng->total_frames - eng->current_frame;
        int toCopy = (numFrames < remaining) ? numFrames : (int)remaining;

        if (toCopy > 0) {
            memcpy(out,
                   eng->pcm + (size_t)eng->current_frame * eng->channels,
                   (size_t)toCopy * eng->channels * sizeof(float));
            eng->current_frame += toCopy;
            if (toCopy < numFrames) {
                memset(out + (size_t)toCopy * eng->channels, 0,
                       (size_t)(numFrames - toCopy) * eng->channels * sizeof(float));
            }
        } else {
            // 播放结束
            memset(out, 0, (size_t)numFrames * eng->channels * sizeof(float));
            eng->is_playing = 0;
            return oboe::DataCallbackResult::Stop;
        }
        return oboe::DataCallbackResult::Continue;
    }
};

// 回调对象与 stream 绑定，随 stream 一起销毁
static std::shared_ptr<EngineCallback> g_callback;

// ─── 引擎 API ───────────────────────────────────────────

AudioEngine* engine_create(void) {
    AudioEngine* eng = new AudioEngine();
    return eng;
}

int engine_load_pcm(AudioEngine* eng, float* pcm,
                    int sampleRate, int channels, long totalFrames) {
    if (!eng || !pcm || sampleRate <= 0 || channels <= 0 || totalFrames <= 0)
        return -1;

    // 释放旧的 PCM
    if (eng->pcm) { delete[] eng->pcm; eng->pcm = nullptr; }

    eng->pcm = pcm;          // 接管所有权
    eng->sample_rate = sampleRate;
    eng->channels = channels;
    eng->total_frames = totalFrames;
    eng->current_frame = 0;
    eng->is_playing = 0;
    return 0;
}

void engine_play(AudioEngine* eng) {
    if (!eng || !eng->pcm) return;
    if (eng->is_playing) return;

    // 如果已有 stream，直接 resume
    if (eng->stream) {
        eng->stream->requestStart();
        eng->is_playing = 1;
        return;
    }

    auto callback = std::make_shared<EngineCallback>();
    callback->eng = eng;
    g_callback = callback;

    auto builder = std::make_shared<oboe::AudioStreamBuilder>();
    builder->setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(eng->channels)
           ->setSampleRate(eng->sample_rate)
           ->setSharingMode(oboe::SharingMode::Shared)   // 兼容所有设备
           ->setDataCallback(callback.get());

    oboe::AudioStream* streamPtr = nullptr;
    oboe::Result r = builder->openStream(&streamPtr);
    if (r != oboe::Result::OK) return;

    eng->stream = std::shared_ptr<oboe::AudioStream>(streamPtr);
    r = eng->stream->requestStart();
    if (r != oboe::Result::OK) {
        eng->stream->close();
        eng->stream.reset();
        g_callback.reset();
        return;
    }
    eng->is_playing = 1;
}

void engine_pause(AudioEngine* eng) {
    if (!eng || !eng->stream) return;
    eng->stream->requestPause();   // 只暂停，不停止
    eng->is_playing = 0;
}

void engine_seek(AudioEngine* eng, long positionMs) {
    if (!eng) return;
    eng->seek_target_ms = positionMs;
    eng->seek_requested = 1;
}

long engine_get_position_ms(AudioEngine* eng) {
    if (!eng || eng->sample_rate <= 0) return 0;
    return (eng->current_frame * 1000) / eng->sample_rate;
}

long engine_get_duration_ms(AudioEngine* eng) {
    if (!eng || eng->sample_rate <= 0) return 0;
    return (eng->total_frames * 1000) / eng->sample_rate;
}

int engine_is_playing(AudioEngine* eng) {
    if (!eng) return 0;
    return eng->is_playing;
}

void engine_release(AudioEngine* eng) {
    if (!eng) return;
    if (eng->stream) {
        eng->stream->requestStop();
        eng->stream->close();
        eng->stream.reset();
    }
    g_callback.reset();
    if (eng->pcm) { delete[] eng->pcm; eng->pcm = nullptr; }
    eng->is_playing = 0;
    delete eng;
}
