package com.apptracker.data.model

data class SensorAccessInfo(
    val sensorType: SensorType,
    val isDeclared: Boolean,
    val lastAccessTime: Long?,
    val accessCount: Int,
    val isCurrentlyActive: Boolean
)

enum class SensorType(val displayName: String, val iconName: String) {
    CAMERA("Camera", "camera"),
    MICROPHONE("Microphone", "mic"),
    LOCATION_GPS("GPS Location", "location_on"),
    LOCATION_NETWORK("Network Location", "my_location"),
    ACCELEROMETER("Accelerometer", "screen_rotation"),
    GYROSCOPE("Gyroscope", "360"),
    PROXIMITY("Proximity", "sensors"),
    FINGERPRINT("Fingerprint", "fingerprint"),
    LIGHT("Light Sensor", "light_mode"),
    BAROMETER("Barometer", "speed"),
    MAGNETOMETER("Magnetometer", "explore"),
    HEART_RATE("Heart Rate", "monitor_heart"),
    STEP_COUNTER("Step Counter", "directions_walk"),
    BODY_TEMPERATURE("Body Temperature", "thermostat"),
    NFC("NFC", "nfc"),
    BLUETOOTH("Bluetooth", "bluetooth")
}
