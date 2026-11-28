# MVP: no custom rules yet.

# kotlinx.serialization: keep generated serializers reachable through the
# reflective serializer() lookups the runtime performs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.novaredux.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class com.novaredux.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.novaredux.launcher.**$$serializer { *; }
