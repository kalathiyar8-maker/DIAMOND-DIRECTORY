package com.diamonddirectory.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "સંપર્ક"
        val phone = intent.getStringExtra("phone") ?: ""
        val chId = "dd_reminder"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(chId, "Call Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(ctx, phone.hashCode(), dial, flags)
        val n = NotificationCompat.Builder(ctx, chId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("\uD83D\uDCDE કોલ રિમાઇન્ડર")
            .setContentText("$name ને કોલ કરવાનું છે")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}
