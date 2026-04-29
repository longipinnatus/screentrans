# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in AppData\Local\Android\Sdk\tools\proguard\proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# --- ONNX Runtime ---
# ONNX Runtime uses JNI to call back into Java. These classes must be kept to avoid JNI errors.
-keep class ai.onnxruntime.** { *; }

# --- Gson ---
# Optimized Gson rules to avoid "overly broad" warnings and handle sun.misc.Unsafe.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses, SourceFile, LineNumberTable
-dontwarn sun.misc.Unsafe

# Prevent R8 from removing/obfuscating TypeToken and its anonymous subclasses.
# This fixes "Abstract classes can't be instantiated" error for Gson.
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.internal.LinkedTreeMap

# Keep fields with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Project Data Models ---
# Explicitly keep all models serialized/deserialized by Gson to ensure R8 doesn't strip them.
-keep class com.longipinnatus.screentrans.TranslationEngine$* { *; }
-keep class com.longipinnatus.screentrans.AppSettings* { *; }
-keep class com.longipinnatus.screentrans.TextBlock { *; }
-keep class com.longipinnatus.screentrans.LogItem { *; }
-keep class com.longipinnatus.screentrans.LogEntry { *; }
-keep class com.longipinnatus.screentrans.LogType { *; }
-keep class com.longipinnatus.screentrans.TokenStats { *; }
