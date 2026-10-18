# org.json, OkHttp and Media3 ship their own consumer rules.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# NewPipeExtractor (YouTube): Rhino runs YouTube's player JavaScript via reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.re2j.**
