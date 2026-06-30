-keep class com.fenlight.companion.data.model.** { *; }
# Reflective Moshi model outside data.model (version.json -> UpdateInfo); keep so R8
# doesn't strip its Kotlin metadata in release (would break the updater's parsing).
-keep class com.fenlight.companion.data.update.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
