# kotlinx.serialization
#
# R8 strips the generated .Companion.serializer() members because nothing references them
# statically. Without these rules photo extraction fails ONLY in release builds, only at
# runtime, with a confusing SerializationException — debug builds pass happily.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static <1>$Companion Companion;
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$$serializer INSTANCE;
}

# Our serializable payloads.
-keep class com.talwinter.bptracker.extract.** { *; }

# Room generates implementations reflectively at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Tink backs EncryptedSharedPreferences, where the API key lives.
#
# Its ErrorProne annotations are compile-time only and never ship, so R8 sees dangling
# references to them. Safe to ignore — nothing reads them at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Tink's KeysDownloader (remote keyset fetching) optionally uses google-http-client and
# joda-time. Neither is a dependency here and neither is reachable from
# EncryptedSharedPreferences, so R8 is right to strip it.
#
# Do NOT add `-keep class com.google.crypto.tink.** { *; }` to silence the annotation
# warnings above: that forces KeysDownloader to be retained, which turns these optional
# references into hard missing-class errors. Keep the dontwarns narrow instead.
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
