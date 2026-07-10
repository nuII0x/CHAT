#include <jni.h>

namespace {

constexpr const char * kUnavailableMessage =
    "Null IA indisponivel neste aparelho. O chat funciona normalmente, "
    "mas a IA local exige Android ARM 64 bits (arm64-v8a).";

jstring make_jstring(JNIEnv * env, const char * value) {
    return env->NewStringUTF(value);
}

} // namespace

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativeIsSupported(
    JNIEnv *,
    jobject
) {
    return JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativePrepare(
    JNIEnv * env,
    jobject,
    jstring,
    jboolean
) {
    return make_jstring(env, kUnavailableMessage);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativeGenerate(
    JNIEnv * env,
    jobject,
    jstring,
    jstring
) {
    return make_jstring(env, kUnavailableMessage);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativeShutdown(
    JNIEnv *,
    jobject
) {
}
