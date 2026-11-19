package com.jonathan.neythan

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.LinkedList

class NeythanNotificationListener : NotificationListenerService() {

    companion object {
        private val historialMensajes = LinkedList<String>()
        private const val MAX_NOTIFS = 8

        fun obtenerResumenNotificaciones(): String {
            synchronized(historialMensajes) {
                if (historialMensajes.isEmpty()) return ""
                return historialMensajes.joinToString(" | ")
            }
        }

        fun tienePermisoNotificaciones(context: Context): Boolean {
            val cn = ComponentName(context, NeythanNotificationListener::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(cn.flattenToString())
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        val appNombre = when (pkg) {
            "com.whatsapp" -> "WhatsApp"
            "com.instagram.android" -> "Instagram"
            "com.google.android.gm" -> "Gmail"
            else -> return
        }

        val extras = sbn.notification?.extras ?: return
        val titulo = extras.getCharSequence("android.title")?.toString()?.trim() ?: ""
        val texto = extras.getCharSequence("android.text")?.toString()?.trim() ?: ""

        if (titulo.isNotEmpty() || texto.isNotEmpty()) {
            val entrada = "[$appNombre] $titulo: $texto"
            synchronized(historialMensajes) {
                if (historialMensajes.size >= MAX_NOTIFS) {
                    historialMensajes.removeFirst()
                }
                historialMensajes.addLast(entrada)
            }
        }
    }
}
