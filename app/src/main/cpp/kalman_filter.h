#pragma once

#include <tuple>

namespace cinematic {

/**
 * 1D Kalman Filter with Constant-Velocity Motion Model.
 * State vector: x = [distance, velocity]^T
 * Tracks physical distance (meters) and operator velocity (m/s).
 */
class KalmanFilter {
public:
    KalmanFilter(double initial_distance,
                 double q_pos = 0.01,
                 double q_vel = 0.1,
                 double r_meas = 0.04);

    void reset(double initial_distance);

    // Predict state forward by dt seconds
    std::pair<double, double> predict(double dt);

    // Update state estimate with noisy distance observation
    std::pair<double, double> update(double measured_distance);

    // Extrapolate position forward by delta_t to compensate for actuator lag
    double predict_future(double delta_t) const;

    double get_distance() const { return d_; }
    double get_velocity() const { return v_; }

private:
    double d_{0.0};
    double v_{0.0};

    // Covariance matrix elements: P = [p00, p01; p10, p11]
    double p00_{0.1};
    double p01_{0.0};
    double p10_{0.0};
    double p11_{1.0};

    double q_pos_{0.01};
    double q_vel_{0.1};
    double r_meas_{0.04};
};

} // namespace cinematic

