package com.apptracker.data.util

/**
 * Human-readable descriptions for common Android permissions,
 * explaining what each permission allows an app to do.
 */
object PermissionDescriptions {

    private val descriptions = mapOf(
        // Location
        "android.permission.ACCESS_FINE_LOCATION" to PermissionInfo(
            title = "Precise Location",
            description = "Access your exact GPS location (within a few meters).",
            concern = "HIGH — Tracks exactly where you are at all times"
        ),
        "android.permission.ACCESS_COARSE_LOCATION" to PermissionInfo(
            title = "Approximate Location",
            description = "Access your approximate location (within ~3 km via WiFi/cell towers).",
            concern = "MODERATE — Knows your general area"
        ),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to PermissionInfo(
            title = "Background Location",
            description = "Access your location even when the app is not in use.",
            concern = "CRITICAL — Tracks you 24/7 in the background"
        ),

        // Camera & Microphone
        "android.permission.CAMERA" to PermissionInfo(
            title = "Camera",
            description = "Take photos and record video using the device camera.",
            concern = "HIGH — Can capture images/video at any time"
        ),
        "android.permission.RECORD_AUDIO" to PermissionInfo(
            title = "Microphone",
            description = "Record audio using the device microphone.",
            concern = "HIGH — Can listen to conversations"
        ),

        // Contacts & Calendar
        "android.permission.READ_CONTACTS" to PermissionInfo(
            title = "Read Contacts",
            description = "Read names, phone numbers, emails from your contacts.",
            concern = "HIGH — Access your entire social network"
        ),
        "android.permission.WRITE_CONTACTS" to PermissionInfo(
            title = "Modify Contacts",
            description = "Add, modify, or delete contacts in your address book.",
            concern = "MODERATE — Can alter your contact list"
        ),
        "android.permission.READ_CALENDAR" to PermissionInfo(
            title = "Read Calendar",
            description = "View your calendar events, attendees, and details.",
            concern = "MODERATE — Knows your schedule"
        ),
        "android.permission.WRITE_CALENDAR" to PermissionInfo(
            title = "Modify Calendar",
            description = "Create, edit, or delete calendar events.",
            concern = "LOW — Can manage calendar entries"
        ),

        // Phone & Calls
        "android.permission.READ_PHONE_STATE" to PermissionInfo(
            title = "Phone State",
            description = "Read phone number, IMEI, carrier, call state, SIM info.",
            concern = "HIGH — Unique device identifiers for tracking"
        ),
        "android.permission.READ_PHONE_NUMBERS" to PermissionInfo(
            title = "Phone Numbers",
            description = "Read phone numbers associated with the device.",
            concern = "HIGH — Knows your phone number"
        ),
        "android.permission.CALL_PHONE" to PermissionInfo(
            title = "Make Calls",
            description = "Initiate phone calls without requiring you to confirm.",
            concern = "HIGH — Can place calls (potentially premium-rate)"
        ),
        "android.permission.READ_CALL_LOG" to PermissionInfo(
            title = "Read Call Log",
            description = "View your incoming, outgoing, and missed call history.",
            concern = "HIGH — Knows who you talk to and when"
        ),
        "android.permission.WRITE_CALL_LOG" to PermissionInfo(
            title = "Write Call Log",
            description = "Modify or delete entries in your call history.",
            concern = "MODERATE — Can alter call records"
        ),
        "android.permission.ANSWER_PHONE_CALLS" to PermissionInfo(
            title = "Answer Calls",
            description = "Automatically answer incoming phone calls.",
            concern = "MODERATE — Can answer calls without your interaction"
        ),

        // SMS & MMS
        "android.permission.SEND_SMS" to PermissionInfo(
            title = "Send SMS",
            description = "Send text messages, potentially to premium-rate numbers.",
            concern = "CRITICAL — Can send SMS that may incur charges"
        ),
        "android.permission.READ_SMS" to PermissionInfo(
            title = "Read SMS",
            description = "Read all text messages stored on the device.",
            concern = "CRITICAL — Access to 2FA codes, personal messages"
        ),
        "android.permission.RECEIVE_SMS" to PermissionInfo(
            title = "Receive SMS",
            description = "Intercept and read incoming text messages.",
            concern = "CRITICAL — Can intercept verification codes"
        ),
        "android.permission.RECEIVE_MMS" to PermissionInfo(
            title = "Receive MMS",
            description = "Intercept and read incoming multimedia messages.",
            concern = "HIGH — Can read multimedia messages"
        ),

        // Storage
        "android.permission.READ_EXTERNAL_STORAGE" to PermissionInfo(
            title = "Read Storage",
            description = "Read files from shared/external storage (photos, downloads, etc.).",
            concern = "HIGH — Can access your photos, documents, downloads"
        ),
        "android.permission.WRITE_EXTERNAL_STORAGE" to PermissionInfo(
            title = "Write Storage",
            description = "Create, modify, or delete files on shared storage.",
            concern = "MODERATE — Can modify files on your device"
        ),
        "android.permission.READ_MEDIA_IMAGES" to PermissionInfo(
            title = "Read Photos",
            description = "Access photos and images stored on your device.",
            concern = "HIGH — Can view all your photos"
        ),
        "android.permission.READ_MEDIA_VIDEO" to PermissionInfo(
            title = "Read Videos",
            description = "Access video files stored on your device.",
            concern = "HIGH — Can view all your videos"
        ),
        "android.permission.READ_MEDIA_AUDIO" to PermissionInfo(
            title = "Read Audio Files",
            description = "Access music and audio files stored on your device.",
            concern = "LOW — Can access your audio library"
        ),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to PermissionInfo(
            title = "All Files Access",
            description = "Read and modify ALL files on the device, including app-specific dirs.",
            concern = "CRITICAL — Unrestricted file system access"
        ),

        // Body Sensors
        "android.permission.BODY_SENSORS" to PermissionInfo(
            title = "Body Sensors",
            description = "Access data from body sensors like heart rate monitors.",
            concern = "HIGH — Reads health/biometric data"
        ),
        "android.permission.ACTIVITY_RECOGNITION" to PermissionInfo(
            title = "Activity Recognition",
            description = "Detect physical activity (walking, running, driving, etc.).",
            concern = "MODERATE — Knows what you're doing physically"
        ),

        // Bluetooth & WiFi
        "android.permission.BLUETOOTH_CONNECT" to PermissionInfo(
            title = "Bluetooth Connect",
            description = "Connect to paired Bluetooth devices.",
            concern = "MODERATE — Can interact with nearby devices"
        ),
        "android.permission.BLUETOOTH_SCAN" to PermissionInfo(
            title = "Bluetooth Scan",
            description = "Discover and scan for nearby Bluetooth devices.",
            concern = "MODERATE — Can detect nearby devices"
        ),
        "android.permission.NEARBY_WIFI_DEVICES" to PermissionInfo(
            title = "Nearby WiFi Devices",
            description = "Discover and connect to nearby WiFi devices.",
            concern = "MODERATE — Can scan local network"
        ),

        // Notifications & UI
        "android.permission.POST_NOTIFICATIONS" to PermissionInfo(
            title = "Show Notifications",
            description = "Display notifications on your device.",
            concern = "LOW — Can show alerts and messages"
        ),
        "android.permission.SYSTEM_ALERT_WINDOW" to PermissionInfo(
            title = "Draw Over Other Apps",
            description = "Display content on top of other apps (overlays).",
            concern = "CRITICAL — Can overlay fake UIs for phishing"
        ),

        // Special
        "android.permission.REQUEST_INSTALL_PACKAGES" to PermissionInfo(
            title = "Install Apps",
            description = "Request to install other application packages.",
            concern = "CRITICAL — Can install additional apps/malware"
        ),
        "android.permission.REQUEST_DELETE_PACKAGES" to PermissionInfo(
            title = "Delete Apps",
            description = "Request to uninstall application packages.",
            concern = "MODERATE — Can prompt to remove apps"
        ),
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to PermissionInfo(
            title = "Accessibility Service",
            description = "Read screen content, interact with apps on your behalf.",
            concern = "CRITICAL — Can see everything on screen, tap buttons"
        ),
        "android.permission.BIND_DEVICE_ADMIN" to PermissionInfo(
            title = "Device Administrator",
            description = "Control device policies: lock screen, wipe data, set passwords.",
            concern = "CRITICAL — Full device management control"
        ),
        "android.permission.BIND_VPN_SERVICE" to PermissionInfo(
            title = "VPN Service",
            description = "Route all network traffic through the app.",
            concern = "CRITICAL — Can intercept ALL network traffic"
        ),
        "android.permission.FOREGROUND_SERVICE" to PermissionInfo(
            title = "Foreground Service",
            description = "Run persistent background services with a notification.",
            concern = "LOW — Runs continuously in background"
        ),
        "android.permission.RECEIVE_BOOT_COMPLETED" to PermissionInfo(
            title = "Run at Startup",
            description = "Automatically start when the device boots up.",
            concern = "MODERATE — Starts without user interaction"
        ),
        "android.permission.WAKE_LOCK" to PermissionInfo(
            title = "Prevent Sleep",
            description = "Keep the processor awake, preventing the device from sleeping.",
            concern = "MODERATE — Can drain battery by keeping device awake"
        ),
        "android.permission.INTERNET" to PermissionInfo(
            title = "Internet Access",
            description = "Access the internet to send and receive data.",
            concern = "LOW — Basic network access (most apps need this)"
        ),
        "android.permission.ACCESS_NETWORK_STATE" to PermissionInfo(
            title = "Network State",
            description = "View information about network connections (WiFi, mobile).",
            concern = "LOW — Sees if you're online and connection type"
        ),
        "android.permission.ACCESS_WIFI_STATE" to PermissionInfo(
            title = "WiFi State",
            description = "View information about WiFi networking.",
            concern = "LOW — Can see WiFi details"
        ),
        "android.permission.VIBRATE" to PermissionInfo(
            title = "Vibrate",
            description = "Control the vibration motor.",
            concern = "NONE — Harmless utility permission"
        ),
        "android.permission.USE_BIOMETRIC" to PermissionInfo(
            title = "Biometrics",
            description = "Use fingerprint or face recognition hardware.",
            concern = "LOW — Uses biometric hardware for authentication"
        ),
        "android.permission.USE_FINGERPRINT" to PermissionInfo(
            title = "Fingerprint",
            description = "Use the fingerprint sensor for authentication.",
            concern = "LOW — Uses fingerprint hardware for auth"
        ),
        "android.permission.NFC" to PermissionInfo(
            title = "NFC Access",
            description = "Perform NFC (Near Field Communication) operations.",
            concern = "MODERATE — Can read/write NFC tags, make payments"
        ),
        "android.permission.QUERY_ALL_PACKAGES" to PermissionInfo(
            title = "Query All Packages",
            description = "See a list of all installed apps on the device.",
            concern = "MODERATE — Knows every app you've installed"
        ),
        "android.permission.PACKAGE_USAGE_STATS" to PermissionInfo(
            title = "Usage Statistics",
            description = "Access app usage history and statistics.",
            concern = "HIGH — Knows how long you use each app"
        ),
        "android.permission.WRITE_SETTINGS" to PermissionInfo(
            title = "Modify Settings",
            description = "Read or write system settings.",
            concern = "MODERATE — Can change device settings"
        ),
        "android.permission.GET_ACCOUNTS" to PermissionInfo(
            title = "Get Accounts",
            description = "Access the list of accounts registered on the device.",
            concern = "HIGH — Can see your email and account names"
        )
    )

    fun getInfo(permissionName: String): PermissionInfo? {
        return descriptions[permissionName]
    }

    fun getDescription(permissionName: String): String {
        return descriptions[permissionName]?.description
            ?: "No description available for this permission."
    }

    fun getConcernLevel(permissionName: String): String {
        return descriptions[permissionName]?.concern ?: "UNKNOWN"
    }

    fun getFriendlyName(permissionName: String): String {
        return descriptions[permissionName]?.title
            ?: permissionName.substringAfterLast(".").replace("_", " ")
                .lowercase().replaceFirstChar { it.uppercase() }
    }

    data class PermissionInfo(
        val title: String,
        val description: String,
        val concern: String
    )
}
