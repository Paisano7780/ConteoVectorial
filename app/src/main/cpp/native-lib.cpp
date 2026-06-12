// ====================================================================
// Bloque 1: Cabeceras e Imports
// ====================================================================
#include <jni.h>
#include <string>
#include <vector>
#include <android/asset_manager_jni.h>
#include <android/log.h>

// Librerías de NCNN (Motor de Inferencia Edge)
#include "net.h"
#include "cpu.h"

// Librerías de OpenCV (Para manejo matricial)
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

// Macros para imprimir en el Logcat de Android Studio de forma fácil
#define TAG "VisionEngineC++"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)


// ====================================================================
// Bloque 2: Variables Globales y Carga del Modelo
// ====================================================================

// Instancia global de la red neuronal. Queda viva en memoria.
ncnn::Net yolov8_pose;

extern "C"
JNIEXPORT jboolean JNICALL
// Firma JNI corregida para conectar con NativeVisionBridge
Java_com_desdelaire_vectorcount_nativebridge_NativeVisionBridge_initModel(JNIEnv *env, jobject thiz, jobject assetManager, jstring param_path, jstring bin_path) {
    
    LOGD("Iniciando carga del modelo NCNN...");

    // Obtenemos el AssetManager de Android para leer los archivos dentro del APK
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("Error fatal: No se pudo obtener el AssetManager");
        return JNI_FALSE;
    }

    // Convertimos los strings de Java a C++
    const char* param = env->GetStringUTFChars(param_path, 0);
    const char* bin = env->GetStringUTFChars(bin_path, 0);

    // Limpiamos cualquier modelo anterior por seguridad
    yolov8_pose.clear();

    // Optimizaciones específicas para celulares ARM
    ncnn::Option opt;
    opt.lightmode = true;
    opt.num_threads = ncnn::get_big_cpu_count(); // Fuerza a usar los núcleos rápidos del procesador
    yolov8_pose.opt = opt;

    // Cargamos la arquitectura (.param) y los pesos (.bin)
    int ret_param = yolov8_pose.load_param(mgr, param);
    int ret_bin = yolov8_pose.load_model(mgr, bin);

    // Liberamos la memoria de los strings
    env->ReleaseStringUTFChars(param_path, param);
    env->ReleaseStringUTFChars(bin_path, bin);

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Error al cargar los archivos de NCNN. Verifica que existan en la carpeta assets.");
        return JNI_FALSE;
    }

    LOGD("Modelo YOLOv8-Pose cargado exitosamente en la RAM del Xiaomi.");
    return JNI_TRUE;
}

// ====================================================================
// Bloque 3, 4 y 5 unificados: El Motor de Procesamiento por Frame
// ====================================================================

extern "C"
JNIEXPORT jfloatArray JNICALL
// Firma JNI corregida para conectar con NativeVisionBridge
Java_com_desdelaire_vectorcount_nativebridge_NativeVisionBridge_processFrame(JNIEnv *env, jobject thiz, jbyteArray yuv_data, jint width, jint height) {
    
    // --- BLOQUE 3: RECEPCIÓN DEL VIDEO ---
    jbyte* data = env->GetByteArrayElements(yuv_data, NULL);
    if (data == NULL) {
        LOGE("Error: No llegaron datos del frame de video.");
        return NULL;
    }

    cv::Mat yuv_mat(height + height / 2, width, CV_8UC1, (unsigned char*)data);
    cv::Mat rgb_mat;
    cv::cvtColor(yuv_mat, rgb_mat, cv::COLOR_YUV2RGBA_NV21);

    // Liberamos la memoria del array original para no saturar la RAM
    env->ReleaseByteArrayElements(yuv_data, data, JNI_ABORT);

    // --- BLOQUE 4: PRE-PROCESAMIENTO ---
    const int target_size = 640; 
    ncnn::Mat in = ncnn::Mat::from_pixels_resize(rgb_mat.data, 
                                                 ncnn::Mat::PIXEL_RGBA2RGB, 
                                                 width, height, 
                                                 target_size, target_size);

    const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    in.substract_mean_normalize(0, norm_vals);

    // --- BLOQUE 5: INFERENCIA Y EXTRACCIÓN DE VECTORES ---
    
    // 1. Creamos el extractor y le pasamos la imagen normalizada
    ncnn::Extractor ex = yolov8_pose.create_extractor();
    ex.input("in0", in); // "in0" es el nombre estándar del nodo de entrada en NCNN

    // 2. Ejecutamos la red neuronal
    ncnn::Mat out;
    ex.extract("out0", out); // "out0" es el nombre del tensor de salida

    // 3. Array dinámico de C++ para guardar nuestros vectores detectados
    std::vector<float> vector_results;

    // 4. Lógica de barrido (Parseo del tensor)
    // El modelo YOLO-Pose escupe una matriz plana. Recorremos esa matriz.
    // (Nota: Esta es la estructura base. El barrido exacto dependerá de cómo 
    // exportemos finalmente el archivo .bin desde PyTorch).
    
    for (int i = 0; i < out.h; i++) {
        const float* values = out.row(i);
        
        // Supongamos que el índice 4 es la confianza de que haya una vaca
        float confidence = values[4]; 
        
        // Filtro de precisión: Solo nos importan vacas con más de 60% de certeza
        if (confidence > 0.6f) {
            
            // Ignoramos los valores de la Bounding Box (índices 0 al 3) 
            // y saltamos directo a los Keypoints (ej: a partir del índice 5).
            
            // Punto 1: Cola
            float cola_x = values[5];
            float cola_y = values[6];
            
            // Punto 2: Cabeza
            float cabeza_x = values[8];
            float cabeza_y = values[9];

            // Guardamos las coordenadas en nuestro array (Formato: [X1, Y1, X2, Y2])
            vector_results.push_back(cola_x);
            vector_results.push_back(cola_y);
            vector_results.push_back(cabeza_x);
            vector_results.push_back(cabeza_y);
        }
    }

    // 5. Traducción final de C++ a Kotlin
    // Convertimos nuestro std::vector dinámico en un jfloatArray estricto que Kotlin pueda entender
    int vector_size = vector_results.size();
    jfloatArray jOutputArray = env->NewFloatArray(vector_size);
    
    if (vector_size > 0) {
        env->SetFloatArrayRegion(jOutputArray, 0, vector_size, vector_results.data());
    }

    // Le devolvemos a Kotlin la lista de vectores puros.
    return jOutputArray;
}
