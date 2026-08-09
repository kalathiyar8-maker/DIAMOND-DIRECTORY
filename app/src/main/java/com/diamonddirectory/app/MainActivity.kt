package com.diamonddirectory.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var listBox: LinearLayout
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.load(this)
        buildUi()
        requestPerms()
        ensureOverlay()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun pad(v: Int) = (v * dp).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEEF2F8.toInt())
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B1E3B.toInt())
            setPadding(pad(16), pad(16), pad(16), pad(16))
        }
        header.addView(TextView(this).apply {
            text = "💎 Diamond Directory"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, pad(12), 0, 0)
        }
        btnRow.addView(flatBtn("+ નવો") { addOrEdit(null) })
        btnRow.addView(flatBtn("ડિપાર્ટમેન્ટ") { manageDepts() })
        btnRow.addView(flatBtn("પરમિશન") { requestPerms(); ensureOverlay() })
        header.addView(btnRow)
        root.addView(header)

        // List
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(12), pad(8), pad(12), pad(24))
        }
        val scroll = ScrollView(this)
        scroll.addView(listBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun flatBtn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            val lp = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            lp.marginEnd = pad(6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun refreshList() {
        listBox.removeAllViews()
        if (Store.contacts.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "\nહજી કોઈ સંપર્ક નથી.\n'+ નવો' થી ઉમેરો, અથવા કોલ પતે એટલે પોપ-અપ આવશે."
                setTextColor(0xFF67779A.toInt())
                gravity = Gravity.CENTER
                setPadding(pad(20), pad(40), pad(20), pad(20))
            })
            return
        }
        Store.contacts.sortedBy { it.name.lowercase() }.forEach { c ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(pad(14), pad(12), pad(14), pad(12))
                val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                lp.topMargin = pad(8)
                layoutParams = lp
            }
            card.addView(TextView(this).apply {
                text = c.name; textSize = 16f; setTextColor(0xFF12203A.toInt())
            })
            val sub = buildString {
                if (c.dept.isNotEmpty()) append("[${c.dept}]  ")
                append(c.phone)
            }
            card.addView(TextView(this).apply {
                text = sub; textSize = 13f; setTextColor(0xFF67779A.toInt())
            })
            if (c.note.isNotEmpty()) card.addView(TextView(this).apply {
                text = "📝 ${c.note}"; textSize = 12f; setTextColor(0xFFD9822B.toInt())
            })
            card.setOnClickListener { contactActions(c) }
            listBox.addView(card)
        }
    }

    private fun contactActions(c: Contact) {
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(arrayOf("કોલ કરો", "એડિટ કરો", "ડિલીટ કરો")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone)))
                    1 -> addOrEdit(c)
                    2 -> {
                        Store.contacts.remove(c); Store.save(this); refreshList()
                    }
                }
            }.show()
    }

    private fun addOrEdit(existing: Contact?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(20), pad(8), pad(20), 0)
        }
        val name = EditText(this).apply { hint = "નામ"; setText(existing?.name ?: "") }
        val phone = EditText(this).apply { hint = "ફોન નંબર"; setText(existing?.phone ?: "") }
        val note = EditText(this).apply { hint = "નોટ"; setText(existing?.note ?: "") }
        val spin = Spinner(this)
        val depts = listOf("— કોઈ નહીં —") + Store.departments
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, depts)
        existing?.let { spin.setSelection(depts.indexOf(it.dept).coerceAtLeast(0)) }

        box.addView(name); box.addView(phone)
        box.addView(TextView(this).apply { text = "ડિપાર્ટમેન્ટ"; setPadding(0, pad(10), 0, 0) })
        box.addView(spin); box.addView(note)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "નવો સંપર્ક" else "એડિટ")
            .setView(box)
            .setPositiveButton("સેવ") { _, _ ->
                val nm = name.text.toString().trim()
                val ph = phone.text.toString().trim()
                if (nm.isEmpty() || ph.isEmpty()) {
                    Toast.makeText(this, "નામ અને નંબર જરૂરી છે", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val dept = if (spin.selectedItemPosition == 0) "" else spin.selectedItem.toString()
                if (existing == null) {
                    Store.contacts.add(Contact(Store.newId(), nm, ph, dept, note.text.toString().trim()))
                } else {
                    existing.name = nm; existing.phone = ph; existing.dept = dept
                    existing.note = note.text.toString().trim()
                }
                Store.save(this); refreshList()
            }
            .setNegativeButton("રદ", null)
            .show()
    }

    private fun manageDepts() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(16), pad(8), pad(16), 0)
        }
        val addRow = LinearLayout(this)
        val newName = EditText(this).apply { hint = "નવું ડિપાર્ટમેન્ટ"
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
        val addBtn = Button(this).apply { text = "ઉમેરો" }
        addRow.addView(newName); addRow.addView(addBtn)
        box.addView(addRow)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(holder)

        fun redraw() {
            holder.removeAllViews()
            Store.departments.toList().forEach { d ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, pad(8), 0, pad(8))
                    gravity = Gravity.CENTER_VERTICAL
                }
                val cnt = Store.contacts.count { it.dept == d }
                row.addView(TextView(this).apply {
                    text = "$d  ($cnt)"
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                })
                row.addView(Button(this).apply {
                    text = "🗑️"
                    setOnClickListener { confirmDeleteDept(d) { redraw() } }
                })
                holder.addView(row)
            }
        }
        addBtn.setOnClickListener {
            val v = newName.text.toString().trim()
            if (v.isNotEmpty() && !Store.departments.contains(v)) {
                Store.departments.add(v); Store.save(this); newName.setText(""); redraw()
            }
        }
        redraw()

        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle("ડિપાર્ટમેન્ટ મેનેજ કરો")
            .setView(scroll)
            .setPositiveButton("બંધ કરો", null)
            .show()
    }

    // Two-time confirmation before deleting a department
    private fun confirmDeleteDept(dept: String, after: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage("\"$dept\" ડિપાર્ટમેન્ટ ડિલીટ કરવું છે?")
            .setNegativeButton("ના", null)
            .setPositiveButton("હા") { _, _ ->
                val n = Store.contacts.count { it.dept == dept }
                AlertDialog.Builder(this)
                    .setMessage("ખરેખર ડિલીટ કરવું?\n$n સંપર્ક રહેશે પણ ડિપાર્ટમેન્ટ વગરના થઈ જશે.")
                    .setNegativeButton("ના", null)
                    .setPositiveButton("હા, ડિલીટ") { _, _ ->
                        Store.departments.remove(dept)
                        Store.contacts.forEach { if (it.dept == dept) it.dept = "" }
                        Store.save(this); refreshList(); after()
                    }.show()
            }.show()
    }

    private fun requestPerms() {
        val need = mutableListOf<String>()
        val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        perms.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) need.add(it)
        }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }

    private fun ensureOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("એક પરમિશન બાકી છે")
                .setMessage("કોલ પતે એટલે પોપ-અપ દેખાય એ માટે \"Display over other apps\" ચાલુ કરો.")
                .setPositiveButton("સેટિંગ ખોલો") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                .setNegativeButton("પછી", null)
                .show()
        }
    }
}
