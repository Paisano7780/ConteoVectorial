// ====================================================================
// VectorCount - Motor de vision nativo (NCNN + OpenCV)
// Implementa los simbolos JNI que consume la app:
//   com.desdelaire.vectorcount.vision.VisionProcessor
//     - processFrameToBitmap : decodifica NV21 -> Bitmap (preview en vivo)
//     - detectKeypoints      : inferencia YOLOv8-pose con NCNN
//     - processFrame         : utilitario NV21 -> gris (no usado en el hot path)
// ====================================================================
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

// NCNN (motor de inferencia, prebuilt estatico sin Vulkan)
#include "net.h"
#include "cpu.h"

// OpenCV (manejo matricial / conversion de color)
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#define TAG "VisionEngineC++"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ====================================================================
// Estado global del modelo
// ====================================================================
namespace {
    ncnn::Net g_yolov8_pose;
    bool g_model_loaded = false;
    bool g_model_attempted = false;
    std::mutex g_model_mutex;

    // Rutas de los pesos dentro de assets/
    const char* kParamPath = "models/yolov8_pose.param";
    const char* kBinPath = "models/yolov8_pose.bin";

    // Tamano de entrada de la red (cuadrado)
    const int kTargetSize = 640;

    // Keypoints por defecto (fallback) en coordenadas normalizadas 0..1
    const float kDummyKeypoints[4] = {0.25f, 0.25f, 0.75f, 0.75f};

    // Inicializa NCNN y carga yolov8_pose desde el AssetManager.
    // Se ejecuta una sola vez (lazy). Devuelve true si el modelo quedo cargado.
    bool ensureModelLoaded(JNIEnv* env, jobject assetManager) {
        std::lock_guard<std::mutex> lock(g_model_mutex);
        if (g_model_attempted) {
            return g_model_loaded;
        }
        g_model_attempted = true;

        AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
        if (mgr == nullptr) {
            LOGE("No se pudo obtener el AAssetManager para cargar el modelo");
            return false;
        }

        // Optimizaciones para CPU ARM (sin Vulkan)
        ncnn::Option opt;
        opt.lightmode = true;
        opt.num_threads = ncnn::get_big_cpu_count();
        opt.use_vulkan_compute = false;

        g_yolov8_pose.clear();
        g_yolov8_pose.opt = opt;

        int ret_param = g_yolov8_pose.load_param(mgr, kParamPath);
        int ret_bin = g_yolov8_pose.load_model(mgr, kBinPath);

        if (ret_param != 0 || ret_bin != 0) {
            LOGE("No se pudieron cargar los pesos NCNN (param=%d, bin=%d). "
                 "Verifica que assets/models/yolov8_pose.param/.bin sean los pesos reales.",
                 ret_param, ret_bin);
            g_model_loaded = false;
            return false;
        }

        LOGD("Modelo YOLOv8-pose NCNN cargado correctamente desde assets.");
        g_model_loaded = true;
        return true;
    }

    // Convierte un Bitmap ARGB_8888 (ya bloqueado) a ncnn::Mat RGB redimensionado.
    ncnn::Mat bitmapToNcnnInput(const cv::Mat& rgba) {
        ncnn::Mat in = ncnn::Mat::from_pixels_resize(
                rgba.data,
                ncnn::Mat::PIXEL_RGBA2RGB,
                rgba.cols, rgba.rows,
                kTargetSize, kTargetSize);
        const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
        in.substract_mean_normalize(0, norm_vals);
        return in;
    }

    jfloatArray makeFloatArray(JNIEnv* env, const float* data, int len) {
        jfloatArray arr = env->NewFloatArray(len);
        if (arr != nullptr && len > 0) {
            env->SetFloatArrayRegion(arr, 0, len, data);
        }
        return arr;
    }
}

// ====================================================================
// processFrameToBitmap: NV21 -> Bitmap RGBA (render del stream en vivo)
// ====================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_desdelaire_vectorcount_vision_VisionProcessor_processFrameToBitmap(
        JNIEnv* env,
        jobject /* thiz */,
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
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
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
    if (yuv_elements == nullptr) {
        AndroidBitmap_unlockPixels(env, outBitmap);
        return JNI_FALSE;
    }

    auto* nv21_ptr = reinterpret_cast<unsigned char*>(yuv_elements) + offset;
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, nv21_ptr);
    cv::Mat rgbaMat(height, width, CV_8UC4, pixels);
    cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);

    env->ReleaseByteArrayElements(yuvData, yuv_elements, JNI_ABORT);
    AndroidBitmap_unlockPixels(env, outBitmap);
    return JNI_TRUE;
}

// ====================================================================
// detectKeypoints: inferencia YOLOv8-pose con NCNN sobre un Bitmap
// Inicializa el modelo (lazy) usando el AssetManager y devuelve
// [x1, y1, x2, y2] normalizados (0..1). Si el modelo no esta disponible,
// hace fallback a keypoints dummy para no romper el pipeline HITL.
// ====================================================================
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_desdelaire_vectorcount_vision_VisionProcessor_detectKeypoints(
        JNIEnv* env,
        jobject /* thiz */,
        jobject assetManager,
        jobject bitmap) {

    if (!ensureModelLoaded(env, assetManager)) {
        // Pesos placeholder o ausentes: devolvemos coords por defecto.
        return makeFloatArray(env, kDummyKeypoints, 4);
    }

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return makeFloatArray(env, kDummyKeypoints, 4);
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return makeFloatArray(env, kDummyKeypoints, 4);
    }

    cv::Mat rgba((int) info.height, (int) info.width, CV_8UC4, pixels);
    ncnn::Mat in = bitmapToNcnnInput(rgba);
    AndroidBitmap_unlockPixels(env, bitmap);

    LOGD("Input image shape: %dx%d, NCNN input: %dx%d", info.width, info.height, in.w, in.h);

    ncnn::Extractor ex = g_yolov8_pose.create_extractor();
    ex.input("in0", in);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0 || out.h <= 0) {
        return makeFloatArray(env, kDummyKeypoints, 4);
    }

    // Parseo: tomamos la deteccion de mayor confianza y sus 2 keypoints.
    // Layout esperado por fila: [bbox(0..3), conf(4), kp1x(5), kp1y(6), kp1v(7), kp2x(8), kp2y(9), ...]
    float best_conf = 0.f;
    std::vector<float> best_kp = {kDummyKeypoints[0], kDummyKeypoints[1],
                                  kDummyKeypoints[2], kDummyKeypoints[3]};
    int detection_count = 0;
    for (int i = 0; i < out.h; i++) {
        const float* row = out.row(i);
        float conf = row[4];
        if (conf > 0.6f) {
            detection_count++;
            if (conf > best_conf && out.w >= 10) {
                best_conf = conf;
                // Normalize by the network input size.
                best_kp = {
                    row[5] / kTargetSize, row[6] / kTargetSize,
                    row[8] / kTargetSize, row[9] / kTargetSize
                };
            }
        }
    }
    
    if (best_conf > 0.6f) {
        LOGD("Detection results: detected_objects=%d, best_confidence=%.3f", detection_count, best_conf);
    }

    return makeFloatArray(env, best_kp.data(), (int) best_kp.size());
}

// ====================================================================
// processFrame: utilitario NV21 -> escala de grises (jbyteArray)
// (Declarado en VisionProcessor; no se usa en el hot path actual.)
// ====================================================================
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_desdelaire_vectorcount_vision_VisionProcessor_processFrame(
        JNIEnv* env,
        jobject /* thiz */,
        jbyteArray yuvData,
        jint width,
        jint height) {

    if (width <= 0 || height <= 0) {
        return nullptr;
    }
    jbyte* data = env->GetByteArrayElements(yuvData, nullptr);
    if (data == nullptr) {
        return nullptr;
    }

    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, reinterpret_cast<unsigned char*>(data));
    cv::Mat grayMat;
    cv::cvtColor(yuvMat, grayMat, cv::COLOR_YUV420sp2GRAY);
    env->ReleaseByteArrayElements(yuvData, data, JNI_ABORT);

    int out_len = grayMat.rows * grayMat.cols;
    jbyteArray outArray = env->NewByteArray(out_len);
    if (outArray != nullptr) {
        env->SetByteArrayRegion(outArray, 0, out_len, reinterpret_cast<jbyte*>(grayMat.data));
    }
    return outArray;
}
