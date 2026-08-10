package com.diamonddirectory.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** Shown after a call from a number already saved in Diamond Directory. */
class CallerInfoActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.load(this)
        val c = Store.contacts.find { it.id == intent.getStringExtra("id") }
        if (c == null) { finish(); return }

        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(18), px(20), px(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "\uD83D\uDCC7 સેવ્ડ સંપર્ક"
            textSize = 13f; setTextColor(0xFF67779A.toInt())
        })

        // photo
        if (c.photo.isNotEmpty()) {
            val bmp = try { BitmapFactory.decodeFile(c.photo) } catch (e: Exception) { null }
            if (bmp != null) root.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(px(96), px(96)).apply { topMargin = px(10) }
                setOnClickListener {
                    startActivity(Intent(this@CallerInfoActivity, ImageViewerActivity::class.java)
                        .putExtra("path", c.photo))
                }
            })
        }

        root.addView(TextView(this).apply {
            text = c.name; textSize = 24f; setTextColor(0xFF12203A.toInt())
            setPadding(0, px(10), 0, 0)
        })
        if (c.dept.isNotEmpty()) root.addView(TextView(this).apply {
            text = c.dept; textSize = 14f; setTextColor(0xFF2563C9.toInt())
            setPadding(0, px(4), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = c.phone; textSize = 17f; setTextColor(0xFF12203A.toInt())
            setPadding(0, px(8), 0, 0)
        })
        if (c.note.isNotEmpty()) root.addView(TextView(this).apply {
            text = "\uD83D\uDCDD " + c.note; textSize = 15f; setTextColor(0xFFD9822B.toInt())
            setPadding(px(6), px(12), px(6), 0); gravity = Gravity.CENTER
        })

        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(18), 0, 0)
        }
        btns.addView(Button(this).apply {
            text = "કોલ કરો"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone))); finish()
            }
        })
        btns.addView(Button(this).apply {
            text = "બંધ કરો"
            setOnClickListener { finish() }
        })
        root.addView(btns)

        val scroll = ScrollView(this); scroll.addView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(scroll)
        setTitle("Diamond Directory")
    }
}
