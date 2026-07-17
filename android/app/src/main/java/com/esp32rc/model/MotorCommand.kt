package com.esp32rc.model

data class MotorCommand(val left: Int, val right: Int) {
    companion object {
        val STOP = MotorCommand(0, 0)

        fun fromJoystick(x: Float, y: Float): MotorCommand {
            val forward = (-y * 255).toInt()
            val turn = (x * 255).toInt()
            val left = (forward + turn).coerceIn(-255, 255)
            val right = (forward - turn).coerceIn(-255, 255)
            return MotorCommand(left, right)
        }
    }
}
