package com.diamonddirectory.app

import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** The "Save this number?" popup shown after a call ends (unknown number). */
class SavePopupActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.load(this)
        val number = intent.getStringExtra("number") ?: ""

        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(16), px(16), px(16))
        }

        root.addView(TextView(this).apply {
            text = "\uD83D\uDCDE નવો નંબર મળ્યો"
            textSize = 15f; setTextColor(0xFF67779A.toInt())
        })
        root.addView(TextView(this).apply {
            text = number
            textSize = 22f; setTextColor(0xFF12203A.toInt())
            setPadding(0, px(4), 0, px(10))
        })

        // NAME — auto-filled from phone contacts if the number is saved there
        root.addView(TextView(this).apply { text = "નામ *"; setTextColor(0xFF2563C9.toInt()); setPadding(0, px(6), 0, px(2)) })
        val nameIn = EditText(this).apply { hint = "અહીં નામ લખો" }
        val autoName = lookupName(number)
        if (autoName.isNotEmpty()) {
            nameIn.setText(autoName)
            nameIn.setSelection(autoName.length)
        }
        root.addView(nameIn)

        root.addView(TextView(this).apply { text = "ડિપાર્ટમેન્ટ"; setPadding(0, px(10), 0, px(2)) })
        val deptIn = Spinner(this)
        deptIn.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("— કોઈ નહીં —") + Store.departments)
        root.addView(deptIn)

        root.addView(TextView(this).apply { text = "નોટ"; setPadding(0, px(10), 0, px(2)) })
        val noteIn = EditText(this).apply { hint = "નોટ (વૈકલ્પિક)" }
        root.addView(noteIn)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, px(14), 0, 0)
        }
        btnRow.addView(Button(this).apply { text = "ના"; setOnClickListener { finish() } })
        btnRow.addView(Button(this).apply {
            text = "સેવ કરો"
            setOnClickListener {
                val name = nameIn.text.toString().trim()
                if (name.isEmpty()) {   // name is now mandatory
                    Toast.makeText(this@SavePopupActivity, "પહેલાં નામ લખો", Toast.LENGTH_SHORT).show()
                    nameIn.requestFocus()
                    return@setOnClickListener
                }
                val dept = if (deptIn.selectedItemPosition == 0) "" else deptIn.selectedItem.toString()
                Store.contacts.add(Contact(Store.newId(), name, number, dept, noteIn.text.toString().trim()))
                Store.save(this@SavePopupActivity)
                Toast.makeText(this@SavePopupActivity, "સેવ થયું ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        root.addView(btnRow)

        val scroll = ScrollView(this)
        scroll.addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(scroll)
        setTitle("Diamond Directory")

        // open keyboard on the name field right away
        nameIn.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    /** Get the caller's name: first from phone contacts, then from the call log
     *  (which may hold a carrier / caller-ID name even for a first-time number). */
    private fun lookupName(number: String): String {
        if (number.isBlank()) return ""
        // 1) phone contacts
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) { val n = it.getString(0); if (!n.isNullOrBlank()) return n } }
        } catch (_: Exception) { }
        // 2) call log cached name (carrier CNAP / caller-ID app, if any)
        try {
            val tail = number.filter { it.isDigit() }.takeLast(7)
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                null, null, CallLog.Calls.DATE + " DESC"
            )?.use { cur ->
                var count = 0
                while (cur.moveToNext() && count < 60) {
                    count++
                    val num = cur.getString(0) ?: continue
                    if (num.filter { it.isDigit() }.endsWith(tail)) {
                        val nm = cur.getString(1)
                        if (!nm.isNullOrBlank()) return nm
                    }
                }
            }
        } catch (_: Exception) { }
        return ""
    }
}
