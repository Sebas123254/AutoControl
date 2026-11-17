# =============================================================================
# AutoControl — Reglas R8
# =============================================================================

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Serializadores generados por el plugin
-keepclassmembers @kotlinx.serialization.Serializable class com.sierracode.autocontrol.** {
    *** Companion;
}
-keepclasseswithmembers class com.sierracode.autocontrol.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sierracode.autocontrol.**$$serializer { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- WorkManager + HiltWorker ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep @androidx.hilt.work.HiltWorker class * { *; }

# --- Coil ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**

# --- Room migrations (referenciadas en DatabaseModule) ---
-keep class com.sierracode.autocontrol.data.local.MIGRATION_* { *; }

# --- Entity / DTOs (usadas por reflexión en serialización y Room) ---
-keep class com.sierracode.autocontrol.data.local.entity.** { *; }
-keep class com.sierracode.autocontrol.data.backup.** { *; }

# --- Notificaciones (BroadcastReceiver invocado por el sistema) ---
-keep class com.sierracode.autocontrol.worker.NotificationActionReceiver { *; }

# --- Enums serializados por name() ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}