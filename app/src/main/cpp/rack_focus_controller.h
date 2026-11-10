#pragma once

#include <cmath>
#include <algorithm>

namespace cinematic {

/**
 * Cinematic Rack Focus Controller.
 * Executes smooth focus pulls between Subject A and Subject B using a cubic Hermite (S-curve)
 * easing profile to eliminate jerky lens motor acceleration/deceleration.
 *
 * Diopter formula: D = 1.0 / distance_meters (Camera2 LENS_FOCUS_DISTANCE).
 */
class RackFocusController {
public:
    RackFocusController(double duration_sec = 1.5);

    void configure(double focus_a_diopters, double focus_b_diopters, double duration_sec = 1.5);
    void startPull(double current_time_sec);

    /**
     * Computes the current commanded lens focus distance (in diopters).
     *
     * @param current_time_sec Monotonic timestamp in seconds.
     * @param out_progress Pointer to receive normalized progress [0.0, 1.0].
     * @return Commanded lens focus distance in diopters.
     */
    double update(double current_time_sec, double* out_progress = nullptr);

    bool is_active() const { return is_active_; }
    bool is_finished() const { return is_finished_; }

    double get_focus_a() const { return focus_a_; }
    double get_focus_b() const { return focus_b_; }

private:
    double focus_a_{0.0};
    double focus_b_{0.0};
    double duration_sec_{1.5};
    double start_time_sec_{0.0};
    bool is_active_{false};
    bool is_finished_{false};

    // Cubic Hermite S-Curve easing: f(tau) = 3*tau^2 - 2*tau^3
    static inline double smoothstep(double tau) {
        tau = std::clamp(tau, 0.0, 1.0);
        return tau * tau * (3.0 - 2.0 * tau);
    }
};

} // namespace cinematic

