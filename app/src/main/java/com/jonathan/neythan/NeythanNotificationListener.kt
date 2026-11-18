package com.jonathan.neythan

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

class NeythanNotificationListener : NotificationListenerService() {

    companion object {
        val historialNotificaciones = CopyOnWriteArrayList<String>()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val paquete = sbn?.packageName ?: return
        if (paquete == "com.whatsapp" || paquete == "com.instagram.android") {
            val extras = sbn.notification?.extras
            val titulo = extras?.getString("android.title") ?: "Contacto"
            val texto = extras?.getCharSequence("android.text")?.toString() ?: return

            val origen = if (paquete == "com.whatsapp") "WhatsApp" else "Instagram"
            val registro = "[$origen] $titulo: $texto"

            if (historialNotificaciones.size >= 15) {
                historialNotificaciones.removeAt(0)
            }
            historialNotificaciones.add(registro)
        }
    }
}
