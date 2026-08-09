package com.diamonddirectory.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Detects when a call ends and, if the number is new, opens SavePopupActivity.
 * Requires READ_PHONE_STATE + READ_CALL_LOG (for the incoming number) and
 * "Display over other apps" so the popup can appear after the call.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!number.isNullOrBlank()) lastNumber = number

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                sawCall = true
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (sawCall) {
                    sawCall = false
                    val num = lastNumber
                    lastNumber = null
                    if (!num.isNullOrBlank()) {
                        Store.load(ctx)
                        if (!Store.exists(num)) {
                            val i = Intent(ctx, SavePopupActivity::class.java)
                            i.putExtra("number", num)
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private var lastNumber: String? = null
        private var sawCall = false
    }
}
