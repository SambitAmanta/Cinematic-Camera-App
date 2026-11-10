#include <jni.h>
#include <android/log.h>
#include "dolly_zoom_controller.h"
#include "rack_focus_controller.h"

#define LOG_TAG "CinematicEngineNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cinematic;

extern "C" {

// ==================== DOLLY ZOOM CONTROLLER ====================

JNIEXPORT jlong JNICALL
Java_com_cinematic_camera_engine_DollyZoomNativeBridge_nativeCreate(
        JNIEnv* /* env */,
        jobject /* this */,
        jdouble initial_distance,
        jint direction,
        jint pace,
        jint curve,
        jdouble min_zoom,
        jdouble max_zoom) {
    DollyZoomConfig config;
    config.min_zoom = min_zoom;
    config.max_zoom = max_zoom;

    auto* controller = new DollyZoomController(
            initial_distance,
            static_cast<DollyDirection>(direction),
            static_cast<ZoomPace>(pace),
            static_cast<ZoomCurve>(curve),
            config
    );
    return reinterpret_cast<jlong>(controller);
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_DollyZoomNativeBridge_nativeDestroy(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong native_handle) {
    if (native_handle != 0) {
        auto* controller = reinterpret_cast<DollyZoomController*>(native_handle);
        delete controller;
    }
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_DollyZoomNativeBridge_nativeReset(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong native_handle,
        jdouble initial_distance,
        jint direction,
        jint pace,
        jint curve) {
    if (native_handle != 0) {
        auto* controller = reinterpret_cast<DollyZoomController*>(native_handle);
        controller->reset(
                initial_distance,
                static_cast<DollyDirection>(direction),
                static_cast<ZoomPace>(pace),
                static_cast<ZoomCurve>(curve)
        );
    }
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_DollyZoomNativeBridge_nativeStartMotion(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong native_handle,
        jdouble start_time_sec) {
    if (native_handle != 0) {
        auto* controller = reinterpret_cast<DollyZoomController*>(native_handle);
        controller->startMotion(start_time_sec);
    }
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_DollyZoomNativeBridge_nativeUpdate(
        JNIEnv* env,
        jobject /* this */,
        jlong native_handle,
        jdouble current_time_sec,
        jdouble dt,
        jdoubleArray out_telemetry) {
    if (native_handle == 0 || out_telemetry == nullptr) {
        return;
    }

    auto* controller = reinterpret_cast<DollyZoomController*>(native_handle);
    DollyZoomOutput output = controller->update(current_time_sec, dt);

    jdouble buffer[6];
    buffer[0] = output.commanded_zoom;
    buffer[1] = output.estimated_distance;
    buffer[2] = output.estimated_velocity;
    buffer[3] = static_cast<jdouble>(output.speed_status);
    buffer[4] = output.is_zoom_complete ? 1.0 : 0.0;
    buffer[5] = output.progress;

    env->SetDoubleArrayRegion(out_telemetry, 0, 6, buffer);
}

// ==================== RACK FOCUS CONTROLLER ====================

JNIEXPORT jlong JNICALL
Java_com_cinematic_camera_engine_RackFocusNativeBridge_nativeCreate(
        JNIEnv* /* env */,
        jobject /* this */,
        jdouble duration_sec) {
    auto* controller = new RackFocusController(duration_sec);
    return reinterpret_cast<jlong>(controller);
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_RackFocusNativeBridge_nativeDestroy(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong native_handle) {
    if (native_handle != 0) {
        auto* controller = reinterpret_cast<RackFocusController*>(native_handle);
        delete controller;
    }
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_RackFocusNativeBridge_nativeConfigure(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong native_handle,
        jdouble focus_a,
        jdouble focus_b,
        jdouble duration_sec) {
    if (native_handle != 0) {
        auto* controller = reinterpret_cast<RackFocusController*>(native_handle);
        controller->configure(focus_a, focus_b, duration_sec);
    }
}

JNIEXPORT void JNICALL
Java_com_cinematic_camera_engine_RackFocusNativeBridge_nativeStart(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong native_handle,
        jdouble start_time_sec) {
    if (native_handle != 0) {
        auto* controller = reinterpret_cast<RackFocusController*>(native_handle);
        controller->startPull(start_time_sec);
    }
}

JNIEXPORT jdouble JNICALL
Java_com_cinematic_camera_engine_RackFocusNativeBridge_nativeUpdate(
        JNIEnv* env,
        jobject /* this */,
        jlong native_handle,
        jdouble current_time_sec,
        jdoubleArray out_progress_array) {
    if (native_handle == 0) {
        return 0.0;
    }

    auto* controller = reinterpret_cast<RackFocusController*>(native_handle);
    double progress = 0.0;
    double commanded_diopters = controller->update(current_time_sec, &progress);

    if (out_progress_array != nullptr) {
        jdouble progress_val = progress;
        env->SetDoubleArrayRegion(out_progress_array, 0, 1, &progress_val);
    }

    return commanded_diopters;
}

} // extern "C"

