# ProGuard rules for AppTracker

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.apptracker.data.db.entity.** { *; }

# Keep data models
-keep class com.apptracker.data.model.** { *; }
