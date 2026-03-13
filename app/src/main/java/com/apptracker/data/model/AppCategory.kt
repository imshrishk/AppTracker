package com.apptracker.data.model

enum class AppCategory(val label: String) {
    SOCIAL("Social"),
    COMMUNICATION("Communication"),
    FINANCE("Finance"),
    GAMES("Games"),
    PRODUCTIVITY("Productivity"),
    TOOLS("Tools"),
    BROWSER("Browser"),
    MEDIA("Media"),
    SHOPPING("Shopping"),
    HEALTH("Health"),
    EDUCATION("Education"),
    NEWS("News"),
    SYSTEM("System"),
    OTHER("Other")
}

object AppCategoryDetector {
    fun infer(packageName: String, appName: String, isSystemApp: Boolean): AppCategory {
        if (isSystemApp) return AppCategory.SYSTEM

        val value = "${packageName.lowercase()} ${appName.lowercase()}"
        return when {
            hasAny(value, "instagram", "facebook", "snapchat", "x.com", "twitter", "tiktok", "reddit", "linkedin") -> AppCategory.SOCIAL
            hasAny(value, "whatsapp", "telegram", "signal", "messenger", "discord", "slack", "teams", "mail") -> AppCategory.COMMUNICATION
            hasAny(value, "bank", "pay", "wallet", "upi", "finance", "money", "trading", "invest") -> AppCategory.FINANCE
            hasAny(value, "game", "gaming", "playgames", "supercell", "riot", "ea", "ubisoft") -> AppCategory.GAMES
            hasAny(value, "docs", "drive", "office", "calendar", "notion", "todo", "keep", "productivity") -> AppCategory.PRODUCTIVITY
            hasAny(value, "tool", "utility", "cleaner", "filemanager", "calculator", "scanner") -> AppCategory.TOOLS
            hasAny(value, "chrome", "firefox", "browser", "opera", "edge", "brave") -> AppCategory.BROWSER
            hasAny(value, "music", "video", "player", "netflix", "spotify", "youtube", "stream") -> AppCategory.MEDIA
            hasAny(value, "shop", "amazon", "flipkart", "ebay", "store", "mart") -> AppCategory.SHOPPING
            hasAny(value, "health", "fit", "fitness", "step", "med", "doctor", "wellness") -> AppCategory.HEALTH
            hasAny(value, "learn", "study", "course", "academy", "school", "edu") -> AppCategory.EDUCATION
            hasAny(value, "news", "times", "daily", "post") -> AppCategory.NEWS
            else -> AppCategory.OTHER
        }
    }

    private fun hasAny(value: String, vararg keys: String): Boolean =
        keys.any { value.contains(it) }
}