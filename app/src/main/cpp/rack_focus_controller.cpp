#include "rack_focus_controller.h"

namespace cinematic {

RackFocusController::RackFocusController(double duration_sec)
    : duration_sec_(duration_sec) {}

void RackFocusController::configure(double focus_a_diopters, double focus_b_diopters, double duration_sec) {
    focus_a_ = focus_a_diopters;
    focus_b_ = focus_b_diopters;
    duration_sec_ = std::max(0.2, duration_sec);
    is_active_ = false;
    is_finished_ = false;
}

void RackFocusController::startPull(double current_time_sec) {
    start_time_sec_ = current_time_sec;
    is_active_ = true;
    is_finished_ = false;
}

double RackFocusController::update(double current_time_sec, double* out_progress) {
    if (!is_active_) {
        if (out_progress) *out_progress = is_finished_ ? 1.0 : 0.0;
        return is_finished_ ? focus_b_ : focus_a_;
    }

    double elapsed = current_time_sec - start_time_sec_;
    double tau = elapsed / duration_sec_;

    if (tau >= 1.0) {
        is_active_ = false;
        is_finished_ = true;
        if (out_progress) *out_progress = 1.0;
        return focus_b_;
    }

    // Apply cubic Hermite S-Curve easing
    double eased_progress = smoothstep(tau);
    if (out_progress) *out_progress = eased_progress;

    // Linear interpolation along the eased parameter
    return focus_a_ + (focus_b_ - focus_a_) * eased_progress;
}

} // namespace cinematic

