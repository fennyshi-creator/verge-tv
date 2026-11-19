# Release rules for the sideload build. Playback and Emby JSON must survive R8.

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Gson reads Kotlin data-class fields and the anonymous TypeToken subclasses in EmbyApi.
-keep class com.xxxx.emby_tv.data.model.** { *; }
-keep class com.xxxx.emby_tv.data.session.** { *; }
-keep class com.xxxx.emby_tv.data.remote.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# FSPlayer JNI looks up these classes and members by name. Renaming breaks prepare().
-keep class com.debugly.fsplayer.** { *; }
-keep interface com.debugly.fsplayer.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Service loaders and TV/network libraries that discover classes by name.
-keep class com.azhon.appupdate.** { *; }
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }
-keep class com.google.zxing.** { *; }

-keepclassmembers enum com.xxxx.emby_tv.** { *; }

-dontwarn com.google.gson.**
-dontwarn com.debugly.fsplayer.**
-dontwarn org.nanohttpd.**
-dontwarn fi.iki.elonen.**
