#include "dolly_zoom_controller.h"

namespace cinematic {

DollyZoomController::DollyZoomController(double initial_distance,
                                         DollyDirection direction,
                                         ZoomPace pace,
                                         ZoomCurve curve,
                                         const DollyZoomConfig& config)
    : config_(config),
      direction_(direction),
      pace_(pace),
      curve_(curve),
      filter_(initial_distance) {
    reset(initial_distance, direction, pace, curve);
}

void DollyZoomController::reset(double initial_distance, DollyDirection direction, ZoomPace pace, ZoomCurve curve) {
    direction_ = direction;
    pace_ = pace;
    curve_ = curve;
    d_0_ = std::max(0.5, initial_distance);
    is_active_ = false;

    // Set duration and optimal walking speed based on selected pace
    switch (pace) {
        case ZoomPace::SLOW:
            config_.duration_sec = 7.0;
            config_.optimal_speed = 0.28;
            config_.speed_tolerance = 0.15;
            break;
        case ZoomPace::FAST:
            config_.duration_sec = 2.5;
            config_.optimal_speed = 0.80;
            config_.speed_tolerance = 0.25;
            break;
        case ZoomPace::MEDIUM:
        default:
            config_.duration_sec = 4.5;
            config_.optimal_speed = 0.45;
            config_.speed_tolerance = 0.20;
            break;
    }

    if (direction_ == DollyDirection::DOLLY_IN) {
        // Dolly-In: starts zoomed in at 3.0x, zooms out to 1.0x as user walks forward
        z_0_ = config_.max_zoom;
        z_target_ = config_.min_zoom;
    } else {
        // Dolly-Out: starts at 1.0x, zooms in to 3.0x as user walks backward
        z_0_ = config_.min_zoom;
        z_target_ = config_.max_zoom;
    }

    current_zoom_ = z_0_;
    filter_.reset(d_0_);
}

void DollyZoomController::startMotion(double start_time_sec) {
    start_time_sec_ = start_time_sec;
    is_active_ = true;
}

DollyZoomOutput DollyZoomController::update(double current_time_sec, double dt) {
    if (!is_active_) {
        return {
            current_zoom_,
            d_0_,
            0.0,
            SpeedStatus::OPTIMAL,
            false,
            0.0
        };
    }

    // First-Frame Anchor: Anchor start_time_sec_ to the exact timestamp of the first frame
    if (start_time_sec_ < 0.0) {
        start_time_sec_ = current_time_sec;
    }

    double elapsed = current_time_sec - start_time_sec_;
    if (elapsed < 0.0) elapsed = 0.0;
    double tau = elapsed / config_.duration_sec;
    bool is_complete = (tau >= 1.0);

    // Evaluate easing based on chosen motion curve profile
    double progress = std::clamp(tau, 0.0, 1.0);
    double eased = progress;
    switch (curve_) {
        case ZoomCurve::S_CURVE:
            eased = smoothstep(progress);
            break;
        case ZoomCurve::DRAMATIC:
            eased = progress * progress;
            break;
        case ZoomCurve::LINEAR:
        default:
            eased = progress;
            break;
    }

    // Commanded zoom ratio interpolated along selected curve
    current_zoom_ = z_0_ + (z_target_ - z_0_) * eased;
    current_zoom_ = std::clamp(current_zoom_, config_.min_zoom, config_.max_zoom);

    // Virtual distance tracking for pacing
    double simulated_dist = (direction_ == DollyDirection::DOLLY_OUT)
        ? d_0_ + (2.0 * progress)
        : d_0_ - (2.0 * progress);

    return {
        current_zoom_,
        simulated_dist,
        config_.optimal_speed,
        SpeedStatus::OPTIMAL,
        is_complete,
        progress
    };
}

} // namespace cinematic
