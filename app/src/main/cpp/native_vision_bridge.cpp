#include <jni.h>
#include <android/log.h>

#define LOG_TAG "NativeVisionBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

/**
 * Stub JNI de inferencia para etapa 1.
 *
 * Estrategia de memoria:
 * - Se fija memoria JVM con GetPrimitiveArrayCritical para minimizar copias.
 * - En integración OpenCV real, este puntero se encapsulará como cv::Mat YUV
 *   y se convertirá a BGR/RGB sólo cuando sea estrictamente necesario.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_desdelaire_vectorcount_nativebridge_NativeVisionBridge_inferKeypointsFromNv21(
        JNIEnv *env,
        jobject /*thiz*/,
        jbyteArray frame_nv21,
        jint offset,
        jint length,
        jint width,
        jint height) {

    if (frame_nv21 == nullptr || width <= 0 || height <= 0 || length <= 0) {
        LOGI("Frame inválido recibido en JNI");
        jfloat fallback[4] = {0.25F, 0.25F, 0.75F, 0.75F};
        jfloatArray output = env->NewFloatArray(4);
        env->SetFloatArrayRegion(output, 0, 4, fallback);
        return output;
    }
    const jsize frameSize = env->GetArrayLength(frame_nv21);
    if (offset < 0 || offset >= frameSize || offset + length > frameSize) {
        LOGI("Frame inválido recibido en JNI");
        jfloat fallback[4] = {0.25F, 0.25F, 0.75F, 0.75F};
        jfloatArray output = env->NewFloatArray(4);
        env->SetFloatArrayRegion(output, 0, 4, fallback);
        return output;
    }
    auto *raw = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(frame_nv21, nullptr));
    if (raw == nullptr) {
        jfloat fallback[4] = {0.25F, 0.25F, 0.75F, 0.75F};
        jfloatArray output = env->NewFloatArray(4);
        env->SetFloatArrayRegion(output, 0, 4, fallback);
        return output;
    }
    auto *nv21 = reinterpret_cast<uint8_t *>(raw + offset);
    (void) nv21;
    env->ReleasePrimitiveArrayCritical(frame_nv21, raw, JNI_ABORT);

    // Mock de inferencia: dos keypoints normalizados A y B.
    const jfloat ax = 0.32F;
    const jfloat ay = 0.38F;
    const jfloat bx = 0.68F;
    const jfloat by = 0.62F;

    jfloat result[4] = {ax, ay, bx, by};
    jfloatArray output = env->NewFloatArray(4);
    env->SetFloatArrayRegion(output, 0, 4, result);
    return output;
}
