package com.diamonddirectory.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!number.isNullOrBlank()) lastNumber = number

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> sawCall = true
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (sawCall) {
                    sawCall = false
                    val num = lastNumber; lastNumber = null
                    if (num.isNullOrBlank()) return
                    Store.load(ctx)
                    val known = Store.findByPhone(num)
                    if (known != null) {
                        known.usage += 1                 // used more -> rises in list
                        Store.save(ctx)
                        if (!Store.popupEnabled) return
                        if (!known.popupOn) return        // this contact's popup turned off
                        val i = Intent(ctx, CallerInfoActivity::class.java)
                        i.putExtra("id", known.id)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    } else {
                        if (!Store.popupEnabled) return
                        if (Store.skipIfInPhonebook && inPhonebook(ctx, num)) return
                        val i = Intent(ctx, SavePopupActivity::class.java)
                        i.putExtra("number", num)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i)
                    }
                }
            }
        }
    }

    private fun inPhonebook(ctx: Context, number: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) { false }
    }

    companion object { private var lastNumber: String? = null; private var sawCall = false }
}
