#include "kalman_filter.h"
#include <algorithm>

namespace cinematic {

KalmanFilter::KalmanFilter(double initial_distance, double q_pos, double q_vel, double r_meas)
    : d_(initial_distance),
      v_(0.0),
      p00_(0.1),
      p01_(0.0),
      p10_(0.0),
      p11_(1.0),
      q_pos_(q_pos),
      q_vel_(q_vel),
      r_meas_(r_meas) {}

void KalmanFilter::reset(double initial_distance) {
    d_ = initial_distance;
    v_ = 0.0;
    p00_ = 0.1;
    p01_ = 0.0;
    p10_ = 0.0;
    p11_ = 1.0;
}

std::pair<double, double> KalmanFilter::predict(double dt) {
    if (dt <= 0.0) {
        return {d_, v_};
    }

    // State extrapolation: x = F * x
    d_ += v_ * dt;

    // Covariance extrapolation: P = F * P * F^T + Q
    double new_p00 = p00_ + dt * (p10_ + p01_) + (dt * dt) * p11_ + q_pos_ * dt;
    double new_p01 = p01_ + dt * p11_;
    double new_p10 = p10_ + dt * p11_;
    double new_p11 = p11_ + q_vel_ * dt;

    p00_ = new_p00;
    p01_ = new_p01;
    p10_ = new_p10;
    p11_ = new_p11;

    return {d_, v_};
}

std::pair<double, double> KalmanFilter::update(double measured_distance) {
    // Innovation (measurement residual): y = z - H * x  (H = [1, 0])
    double y = measured_distance - d_;

    // Innovation covariance: S = H * P * H^T + R
    double s = p00_ + r_meas_;

    // Kalman gain: K = P * H^T * inv(S)
    double k0 = p00_ / s;
    double k1 = p10_ / s;

    // Updated state estimate
    d_ += k0 * y;
    v_ += k1 * y;

    // Updated covariance: P = (I - K * H) * P
    double new_p00 = p00_ - k0 * p00_;
    double new_p01 = p01_ - k0 * p01_;
    double new_p10 = p10_ - k1 * p00_;
    double new_p11 = p11_ - k1 * p01_;

    p00_ = new_p00;
    p01_ = new_p01;
    p10_ = new_p10;
    p11_ = new_p11;

    return {d_, v_};
}

double KalmanFilter::predict_future(double delta_t) const {
    return d_ + v_ * delta_t;
}

} // namespace cinematic

