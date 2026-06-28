#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

/* 不透明句柄 */
typedef struct AudioEngine AudioEngine;

/* 创建引擎实例 */
AudioEngine* engine_create(void);

/* 加载已解码的 float PCM 数据（interleaved）
 * pcm: float 数组，所有权转移到 native（由 engine_release 释放）
 * sampleRate / channels / totalFrames: 音频参数
 */
int engine_load_pcm(AudioEngine* eng, float* pcm,
                    int sampleRate, int channels, long totalFrames);

void engine_play(AudioEngine* eng);
void engine_pause(AudioEngine* eng);
void engine_seek(AudioEngine* eng, long positionMs);
long engine_get_position_ms(AudioEngine* eng);
long engine_get_duration_ms(AudioEngine* eng);
int  engine_is_playing(AudioEngine* eng);
void engine_release(AudioEngine* eng);

#ifdef __cplusplus
}
#endif

#endif
