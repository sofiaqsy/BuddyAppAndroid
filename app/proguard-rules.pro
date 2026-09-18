# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; }
-if @kotlinx.serialization.Serializable class ** { static **$Companion Companion; }
-keepclassmembers class <2>$Companion { kotlinx.serialization.KSerializer serializer(...); }

# Diagnóstico solo en Debug (espejo de dlog en iOS). Durante la
# estabilización se instrumentó el cliente entero —cada petición, cada fix del
# GPS, cada carga—; en release eso no es comportamiento de la app. R8 elimina
# las llamadas a Log.v/d/i en el build minificado. Log.w y Log.e se quedan: una
# release muda es tan difícil de diagnosticar como una que lo grita todo.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
