# --- kotlinx.serialization -----------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sus7898.lrrviewer.**$$serializer { *; }
-keepclassmembers class com.sus7898.lrrviewer.** { *** Companion; }
-keepclasseswithmembers class com.sus7898.lrrviewer.** { kotlinx.serialization.KSerializer serializer(...); }

# --- OkHttp ---------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Keep stack traces readable in crash reports -------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
