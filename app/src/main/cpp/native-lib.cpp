#include <jni.h>
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
