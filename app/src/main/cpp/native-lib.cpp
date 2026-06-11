#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

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
    jbyte* yuv_elements = env->GetByteArrayElements(yuvData, nullptr);
    if (!yuv_elements) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        return JNI_FALSE;
    }
    uchar* nv21_ptr = reinterpret_cast<uchar*>(yuv_elements) + offset;
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, nv21_ptr);
    cv::Mat rgbaMat(height, width, CV_8UC4, pixels);
    cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);
    cv::putText(rgbaMat, "VectorCount Stream Activo", cv::Point(50, 50), cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 255, 0, 255), 2);
    env->ReleaseByteArrayElements(yuvData, yuv_elements, JNI_ABORT);
    AndroidBitmap_unlockPixels(env, outBitmap);
    return JNI_TRUE;
}
