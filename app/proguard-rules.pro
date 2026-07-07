# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; }
-if @kotlinx.serialization.Serializable class ** { static **$Companion Companion; }
-keepclassmembers class <2>$Companion { kotlinx.serialization.KSerializer serializer(...); }
