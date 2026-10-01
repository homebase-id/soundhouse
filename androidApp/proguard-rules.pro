# Shrink and optimise, but keep names so logcat traces read without a mapping file.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# FileKit
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# The vendored API layer (serializers, reflection-loaded pieces); same rule as chat-kmp.
-keep class id.homebase.api.** { *; }

# Ktor server's auto-reload/config module loading needs kotlin-reflect (excluded, unused by embeddedServer);
# the debugger probe needs java.lang.management, which Android lacks.
-dontwarn kotlin.reflect.full.**
-dontwarn kotlin.reflect.jvm.**
-dontwarn java.lang.management.**
