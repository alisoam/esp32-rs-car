#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize MX1508 motor driver: 4x LEDC PWM channels + watchdog task.
 * Must be called once before motor_set().
 */
void motor_control_init(void);

/**
 * Drive both motors. Values are -255..255 (forward positive).
 * Applies ±20 deadband and ±50 per-call ramp limiting.
 * Resets the watchdog timer.
 */
void motor_set(int left, int right);

/**
 * Stop all motors immediately (all PWM channels to 0).
 * Resets the watchdog timer.
 */
void motor_stop(void);

#ifdef __cplusplus
}
#endif
