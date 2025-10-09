package com.example.app_finanzas_ia

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListenerService : NotificationListenerService() {

    private val TAG = "NotificationListener"
    private lateinit var notificationProcessor: NotificationProcessor
    private lateinit var transactionStorage: TransactionStorage

    // Paquetes de apps bancarias a monitorear
    private val MONITORED_PACKAGES = setOf(
        "es.bancosantander.apps",           // Santander
        "com.google.android.apps.walletnfcrel", // Google Pay
        "com.bbva.bbvacontigo",             // BBVA (opcional)
        "com.rsi"                            // Ruralvía (opcional)
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio de notificaciones iniciado")
        notificationProcessor = NotificationProcessor(this)
        transactionStorage = TransactionStorage(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        sbn?.let { notification ->
            val packageName = notification.packageName

            // Verificar si la notificación es de una app monitoreada
            if (MONITORED_PACKAGES.contains(packageName)) {
                Log.d(TAG, "Notificación detectada de: $packageName")
                processNotification(notification)
            }
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras

        // Extraer texto de la notificación
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

        val fullText = "$title\n$bigText"

        Log.d(TAG, "Texto de notificación: $fullText")

        // Procesar en segundo plano
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = notificationProcessor.processNotification(
                    fullText,
                    sbn.packageName
                )

                if (result.success && result.transaction != null) {
                    // Guardar transacción
                    transactionStorage.saveTransaction(result.transaction)

                    // Notificar a la app principal
                    sendBroadcast(Intent("com.example.app_finanzas_ia.NEW_TRANSACTION"))

                    Log.d(TAG, "Transacción guardada: ${result.transaction}")
                } else {
                    Log.w(TAG, "No se pudo procesar la notificación: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando notificación", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Opcional: manejar notificaciones eliminadas
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}