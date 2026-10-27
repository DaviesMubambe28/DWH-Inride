# DHW-inRide release shrinker rules.
# This file is documentation/configuration only until a tested release build
# enables minification in each application module.

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx.serialization generated serializers and wire models.
-keep,includedescriptorclasses class zm.co.dhwinride.**$$serializer { *; }
-keepclassmembers class zm.co.dhwinride.** { *** Companion; }
-keepclasseswithmembers class zm.co.dhwinride.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class zm.co.dhwinride.core.model.** { *; }
-keep class zm.co.dhwinride.core.data.dto.** { *; }

# Runtime-discovered networking and Supabase integrations.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Firebase and Play Services runtime bindings.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.provider.FirebaseInitProvider { *; }

# Application classes used by ViewModel/runtime wiring.
-keep class zm.co.dhwinride.core.ui.**.*ViewModel { *; }
-keep class zm.co.dhwinride.core.ui.**.*ViewModel$* { *; }
-keep class zm.co.dhwinride.core.data.** { *; }

# Remove verbose/debug/info logging from a minified release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
