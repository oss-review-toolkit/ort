# Allow to make some classes public so that they can be repackaged without breaking package-private members.
-allowaccessmodification

# Keep class names to make debugging easier.
-dontobfuscate

# The Gradle API JAR is not added to the classpath, ignore the missing symbols.
-ignorewarnings

# Keep Kotlin metadata so that the Kotlin compiler knows about top-level functions and other things.
-keep class kotlin.Metadata { *; }

# Keep Unit for `kts` compatibility, as functions in a Gradle extension returning a relocated Unit would not work.
-keep class kotlin.Unit { *; }

# Keep functions because they are used in the public API of Gradle/AGP/KGP.
-keep class kotlin.jvm.functions.** { *; }

# kotlin-reflect uses `EnumSetOf` that makes a reflective access to `values`, see
# https://github.com/JetBrains/kotlin/blob/0f9a413ee986f4fd80e26aed2685a1823b2b4279/core/descriptors/src/org/jetbrains/kotlin/builtins/PrimitiveType.java#L39
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
}

# Type arguments need to be kept for Gradle to be able to instantiate abstract models like `Property`.
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable

# Keep the public API so that it is callable from scripts.
-keep class org.ossreviewtoolkit.** { *; }
-keep interface Ort* { *; }

# Tell R8 where to relocate classes to.
-repackageclasses org.ossreviewtoolkit.relocated
