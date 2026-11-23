# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep @Keep annotated Firestore & UI model classes and their members
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class com.example.model.** { *; }
-keepclassmembers class com.example.model.** {
    *;
}

# Preserve CameraX & Credential Manager reflection classes if minification is enabled
-dontwarn androidx.camera.**
-dontwarn androidx.credentials.**
-dontwarn com.google.firebase.**
