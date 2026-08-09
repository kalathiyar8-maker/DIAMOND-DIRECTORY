package com.diamonddirectory.app

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** The "Save this number?" popup shown after a call ends. */
class SavePopupActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.load(this)
        val number = intent.getStringExtra("number") ?: ""

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "📞 નવો નંબર મળ્યો"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = number
            textSize = 22f
            setPadding(0, pad / 2, 0, pad / 2)
        })

        val nameIn = EditText(this).apply { hint = "નામ" }
        val deptIn = Spinner(this)
        val noteIn = EditText(this).apply { hint = "નોટ (વૈકલ્પિક)" }

        val depts = listOf("— કોઈ નહીં —") + Store.departments
        deptIn.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, depts)

        root.addView(TextView(this).apply { text = "નામ"; setPadding(0, pad/2, 0, 4) })
        root.addView(nameIn)
        root.addView(TextView(this).apply { text = "ડિપાર્ટમેન્ટ"; setPadding(0, pad/2, 0, 4) })
        root.addView(deptIn)
        root.addView(TextView(this).apply { text = "નોટ"; setPadding(0, pad/2, 0, 4) })
        root.addView(noteIn)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad, 0, 0)
            gravity = Gravity.END
        }
        val no = Button(this).apply { text = "ના" }
        val save = Button(this).apply { text = "સેવ કરો" }
        no.setOnClickListener { finish() }
        save.setOnClickListener {
            val name = nameIn.text.toString().trim().ifEmpty { number }
            val dept = if (deptIn.selectedItemPosition == 0) "" else deptIn.selectedItem.toString()
            Store.contacts.add(
                Contact(Store.newId(), name, number, dept, noteIn.text.toString().trim())
            )
            Store.save(this)
            Toast.makeText(this, "સેવ થયું ✓", Toast.LENGTH_SHORT).show()
            finish()
        }
        btnRow.addView(no)
        btnRow.addView(save)
        root.addView(btnRow)

        val scroll = ScrollView(this)
        scroll.addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(scroll)
        setTitle("Diamond Directory")
    }
}
