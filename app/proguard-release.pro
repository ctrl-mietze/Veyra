# Veyra Root v1.0.0 hardened release
# Third-party libraries are deliberately kept stable; R8 focuses on Veyra-owned application code.

-dontoptimize
-allowaccessmodification
-adaptclassstrings ctrl.mietze.veyraroot.**
-repackageclasses 'vr'

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute Veyra

# Android entry points stay addressable from the manifest/framework.
-keep class ctrl.mietze.veyraroot.RootMyGalaxyApplication { *; }
-keep class ctrl.mietze.veyraroot.MainActivity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# Legacy JNI ABI exported by libs25u_native.so.
-keep class dev.busung.s25uroot.NativeProbe { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep dependency internals out of the obfuscation problem space. This also avoids the AndroidX
# API-modeling crash seen with R8 9.2.x on-device.
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep interface kotlinx.** { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }
-keep class com.materialkolor.** { *; }
-keep interface com.materialkolor.** { *; }

-dontwarn javax.naming.**
-dontwarn org.bouncycastle.**

# Remove ordinary Android Log calls from the hardened build. Veyra's own AppLog remains available.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
