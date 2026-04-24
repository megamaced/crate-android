# Keep generic signatures for Retrofit / kotlinx.serialization response types
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Retrofit
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization — generated @Serializable metadata must survive R8
-keep,includedescriptorclasses class com.macebox.crate.**$$serializer { *; }
-keepclassmembers class com.macebox.crate.** {
    *** Companion;
}
-keepclasseswithmembers class com.macebox.crate.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generated impls
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }

# Coil / OkHttp internal
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
