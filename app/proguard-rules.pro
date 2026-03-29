# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK's default proguard-android-optimize.txt.

# Keep Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.codrivelog.app.data.model.** { *; }

# Keep data class member names (needed for serialization)
-keepclassmembers class com.codrivelog.app.** {
    public <init>(...);
}
