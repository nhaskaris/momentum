# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations (needed by many libraries)
-keepattributes *Annotation*
-keepattributes Signature

# Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# ML Kit specific rules
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep public class com.google.mlkit.common.internal.model.ModelResource { *; }

# Keep the ComponentRegistrars which are used for discovery
-keep class * implements com.google.firebase.components.ComponentRegistrar
-keep class * implements com.google.mlkit.common.sdkinternal.ModelResource
