# kotlinx.serialization: keep generated serializers of the app's model classes.
-keepclassmembers @kotlinx.serialization.Serializable class de.sensorcharts.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
