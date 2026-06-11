#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

namespace {
const char* OVERLAY_TEXT = "VectorCount Stream Activo";
const int TEXT_X = 50;
const int TEXT_Y = 50;
const double FONT_SCALE = 1.0;
const int THICKNESS = 2;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_desdelaire_vectorcount_vision_VisionProcessor_processFrame(
        JNIEnv *env,
        jobject thiz,
        jbyteArray yuvData,
        jint width,
        jint height) {
    jbyte* yuv_elements = env->GetByteArrayElements(yuvData, nullptr);
    if (!yuv_elements) {
        return nullptr;
    }
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, reinterpret_cast<uchar*>(yuv_elements));
    cv::Mat grayMat;
    cv::cvtColor(yuvMat, grayMat, cv::COLOR_YUV420sp2GRAY);
    jsize graySize = grayMat.total() * grayMat.elemSize();
    jbyteArray outArray = env->NewByteArray(graySize);
    if (!outArray) {
        yuvMat.release();
        grayMat.release();
        env->ReleaseByteArrayElements(yuvData, yuv_elements, 0);
        return nullptr;
    }
    env->SetByteArrayRegion(outArray, 0, graySize, reinterpret_cast<const jbyte*>(grayMat.data));
    yuvMat.release();
    grayMat.release();
    env->ReleaseByteArrayElements(yuvData, yuv_elements, 0);
    return outArray;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_desdelaire_vectorcount_vision_VisionProcessor_processFrameToBitmap(
        JNIEnv *env,
        jobject thiz,
        jbyteArray yuvData,
        jint offset,
        jint length,
        jint width,
        jint height,
        jobject outBitmap) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, outBitmap, &info) < 0) {
        return JNI_FALSE;
    }
    if (AndroidBitmap_lockPixels(env, outBitmap, &pixels) < 0) {
        return JNI_FALSE;
    }
    jsize array_len = env->GetArrayLength(yuvData);
    if (offset < 0 || width <= 0 || height <= 0 || offset + length > array_len) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        return JNI_FALSE;
    }
    int expected_min_len = width * height * 3 / 2;
    if (length < expected_min_len) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        return JNI_FALSE;
    }
    jbyte* yuv_elements = env->GetByteArrayElements(yuvData, nullptr);
    if (!yuv_elements) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        return JNI_FALSE;
    }
    uchar* nv21_ptr = reinterpret_cast<uchar*>(yuv_elements) + offset;
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, nv21_ptr);
    cv::Mat rgbaMat(height, width, CV_8UC4, pixels);
    cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);
    cv::putText(rgbaMat, OVERLAY_TEXT, cv::Point(TEXT_X, TEXT_Y), cv::FONT_HERSHEY_SIMPLEX, FONT_SCALE, cv::Scalar(0, 255, 0, 255), THICKNESS);
    env->ReleaseByteArrayElements(yuvData, yuv_elements, JNI_ABORT);
    AndroidBitmap_unlockPixels(env, outBitmap);
    return JNI_TRUE;
}

#include <ncnn/net.h>
// Instancia global de NCNN
static ncnn::Net yolov8;

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_desdelaire_vectorcount_vision_VisionProcessor_detectKeypoints(JNIEnv *env, jobject thiz, jobject assetManager, jobject bitmap) {
    jfloatArray result = env->NewFloatArray(4);
    jfloat dummy[4] = {0.25f, 0.25f, 0.75f, 0.75f};
    env->SetFloatArrayRegion(result, 0, 4, dummy);
    return result;
}
