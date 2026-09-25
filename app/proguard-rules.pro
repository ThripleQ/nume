# Project-specific R8 rules (minification/shrinking enabled for release).

# --- JNI bridge ---------------------------------------------------------------
# libnetease_jni.c resolves these by *name* via FindClass/GetFieldID/
# GetStaticMethodID and RegisterNatives. Renaming or stripping any of them
# breaks the native transport at runtime, so keep names and members.
-keep class com.thripleq.nume.core.net.NumeNative { *; }
-keep class com.thripleq.nume.core.net.NumeTransport { <methods>; }
-keep class com.thripleq.nume.core.net.NumeTransportOut { <fields>; }
-keep class com.thripleq.nume.core.net.ApiResult { <init>(...); }

# Keep the native method names used in the JNI method table.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
