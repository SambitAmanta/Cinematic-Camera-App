#pragma once

#include "kalman_filter.h"
#include <cmath>
#include <cstdint>
#include <algorithm>

namespace cinematic {

enum class DollyDirection : int32_t {
    DOLLY_IN = 0,   // Operator walks towards subject (zoom starts at 3.0x, zooms out to 1.0x)
    DOLLY_OUT = 1   // Operator walks away from subject (zoom starts at 1.0x, zooms in to 3.0x)
};

enum class ZoomPace : int32_t {
    SLOW = 0,       // 7.0s duration
    MEDIUM = 1,     // 4.5s duration (Default)
    FAST = 2        // 2.5s duration
};

enum class ZoomCurve : int32_t {
    LINEAR = 0,     // Constant velocity: f(tau) = tau (Default, kinetically aligned with walking)
    S_CURVE = 1,    // Smooth cubic Hermite: f(tau) = 3*tau^2 - 2*tau^3 (Smooth acceleration & deceleration)
    DRAMATIC = 2    // Ease-In quadratic crash: f(tau) = tau^2 (Slow start, accelerates towards the end)
};

enum class SpeedStatus : int32_t {
    TOO_SLOW = 0,
    OPTIMAL = 1,
    TOO_FAST = 2
};

struct DollyZoomConfig {
    double min_zoom = 1.0;
    double max_zoom = 3.0;               // 1.0x to 3.0x range on Pixel 8a
    double duration_sec = 4.5;           // Default: Medium pace
    double optimal_speed = 0.45;         // Target walking speed in m/s
    double speed_tolerance = 0.2;
};

struct DollyZoomOutput {
    double commanded_zoom;
    double estimated_distance;
    double estimated_velocity;
    SpeedStatus speed_status;
    bool is_zoom_complete;
    double progress;                     // 0.0 to 1.0
};

class DollyZoomController {
public:
    DollyZoomController(double initial_distance,
                        DollyDirection direction,
                        ZoomPace pace = ZoomPace::MEDIUM,
                        ZoomCurve curve = ZoomCurve::LINEAR,
                        const DollyZoomConfig& config = DollyZoomConfig());

    void reset(double initial_distance, DollyDirection direction, ZoomPace pace, ZoomCurve curve = ZoomCurve::LINEAR);
    void startMotion(double start_time_sec = -1.0);

    /**
     * Executes one cycle of the cinematic zoom law.
     * Guaranteed zero heap allocations in this method.
     *
     * @param current_time_sec Monotonic timestamp in seconds.
     * @param dt Elapsed time since previous cycle.
     * @return DollyZoomOutput containing actuation commands and telemetry.
     */
    DollyZoomOutput update(double current_time_sec, double dt);

    double get_current_zoom() const { return current_zoom_; }
    double get_initial_distance() const { return d_0_; }
    double get_initial_zoom() const { return z_0_; }
    DollyDirection get_direction() const { return direction_; }
    ZoomPace get_pace() const { return pace_; }
    ZoomCurve get_curve() const { return curve_; }

private:
    DollyZoomConfig config_;
    DollyDirection direction_;
    ZoomPace pace_{ZoomPace::MEDIUM};
    ZoomCurve curve_{ZoomCurve::LINEAR};
    double d_0_{2.0};
    double z_0_{1.0};
    double z_target_{3.0};
    double start_time_sec_{0.0};
    bool is_active_{false};

    KalmanFilter filter_;
    double current_zoom_{1.0};

    static inline double smoothstep(double tau) {
        tau = std::clamp(tau, 0.0, 1.0);
        return tau * tau * (3.0 - 2.0 * tau);
    }
};

} // namespace cinematic
