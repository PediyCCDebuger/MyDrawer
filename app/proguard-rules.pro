# MyDrawer keep rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep data classes serialized by kotlinx.serialization (their companion + serializer).
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.pediycc.mydrawer.data.** { kotlinx.serialization.KSerializer serializer(...); }
-if class com.pediycc.mydrawer.data.** { *** Companion; }
-keepclasseswithmembers class com.pediycc.mydrawer.data.** { *; }
