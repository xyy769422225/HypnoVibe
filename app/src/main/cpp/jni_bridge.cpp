#include <jni.h>
#include "audio_engine.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeInit(
    JNIEnv*, jclass) {
    return (jlong)engine_create();
}

JNIEXPORT jboolean JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeLoadPcm(
    JNIEnv* env, jobject, jlong ptr, jfloatArray pcmArray,
    jint sampleRate, jint channels, jlong totalFrames) {
    AudioEngine* eng = (AudioEngine*)ptr;
    if (!eng) return JNI_FALSE;

    jsize len = env->GetArrayLength(pcmArray);
    if (len <= 0) return JNI_FALSE;

    // 拷贝到 native 内存（engine 接管所有权）
    float* pcm = new float[len];
    env->GetFloatArrayRegion(pcmArray, 0, len, pcm);

    int r = engine_load_pcm(eng, pcm, sampleRate, channels, totalFrames);
    return r == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativePlay(
    JNIEnv*, jobject, jlong ptr) {
    AudioEngine* eng = (AudioEngine*)ptr;
    if (eng) engine_play(eng);
}

JNIEXPORT void JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativePause(
    JNIEnv*, jobject, jlong ptr) {
    AudioEngine* eng = (AudioEngine*)ptr;
    if (eng) engine_pause(eng);
}

JNIEXPORT void JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeSeek(
    JNIEnv*, jobject, jlong ptr, jlong ms) {
    AudioEngine* eng = (AudioEngine*)ptr;
    if (eng) engine_seek(eng, ms);
}

JNIEXPORT jlong JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeGetPosition(
    JNIEnv*, jobject, jlong ptr) {
    AudioEngine* eng = (AudioEngine*)ptr;
    return eng ? engine_get_position_ms(eng) : 0;
}

JNIEXPORT jlong JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeGetDuration(
    JNIEnv*, jobject, jlong ptr) {
    AudioEngine* eng = (AudioEngine*)ptr;
    return eng ? engine_get_duration_ms(eng) : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeIsPlaying(
    JNIEnv*, jobject, jlong ptr) {
    AudioEngine* eng = (AudioEngine*)ptr;
    return eng && engine_is_playing(eng) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_hypno_hypnovibe_infrastructure_audio_AudioEngine_nativeRelease(
    JNIEnv*, jobject, jlong ptr) {
    AudioEngine* eng = (AudioEngine*)ptr;
    if (eng) engine_release(eng);
}

} // extern "C"
