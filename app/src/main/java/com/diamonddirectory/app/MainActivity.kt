package com.diamonddirectory.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var listBox: LinearLayout
    private lateinit var panelInner: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var searchInput: EditText

    private var currentDept: String? = null
    private var query: String = ""

    private var pendingPhoto: ((Uri) -> Unit)? = null
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pendingPhoto; pendingPhoto = null
        if (uri != null && cb != null) cb(uri)
    }

    private val guDays = arrayOf("રવિવાર","સોમવાર","મંગળવાર","બુધવાર","ગુરુવાર","શુક્રવાર","શનિવાર")
    private val dp get() = resources.displayMetrics.density
    private fun pad(v: Int) = (v * dp).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.load(this)
        buildUi()
        requestPerms()
        ensureOverlay()
    }

    override fun onResume() {
        super.onResume()
        buildDrawer()
        refreshList()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START)
        else super.onBackPressed()
    }

    // ---------------- UI shell ----------------
    private fun buildUi() {
        drawer = DrawerLayout(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEEF2F8.toInt())
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0B1E3B.toInt())
            setPadding(pad(6), pad(10), pad(10), pad(10))
        }
        val menuBtn = Button(this).apply {
            text = "\u2630"; textSize = 18f
            setOnClickListener { drawer.openDrawer(GravityCompat.START) }
        }
        titleView = TextView(this).apply {
            text = "\uD83D\uDC8E બધા સંપર્ક"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 18f
            setPadding(pad(6), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val addBtn = Button(this).apply {
            text = "+ નવો"; textSize = 12f
            setOnClickListener { addOrEdit(null) }
        }
        bar.addView(menuBtn); bar.addView(titleView); bar.addView(addBtn)
        content.addView(bar)

        searchInput = EditText(this).apply {
            hint = "\uD83D\uDD0D નામ, નંબર કે ડિપાર્ટમેન્ટ શોધો…"
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(pad(14), pad(12), pad(14), pad(12))
            setSingleLine(true)
        }
        searchInput.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(pad(10), pad(10), pad(10), 0)
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { query = e?.toString()?.trim() ?: ""; refreshList() }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        content.addView(searchInput)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(12), pad(8), pad(12), pad(24))
        }
        val scroll = ScrollView(this)
        scroll.addView(listBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        drawer.addView(content, DrawerLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        panelInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(16), pad(20), pad(16), pad(24))
        }
        val panel = ScrollView(this).apply { setBackgroundColor(0xFFFFFFFF.toInt()) }
        panel.addView(panelInner)
        val plp = DrawerLayout.LayoutParams(pad(290), MATCH_PARENT)
        plp.gravity = Gravity.START
        drawer.addView(panel, plp)

        setContentView(drawer)
    }

    // ---------------- side menu ----------------
    private fun buildDrawer() {
        panelInner.removeAllViews()

        panelInner.addView(TextView(this).apply {
            text = "\uD83D\uDC8E Diamond Directory"; textSize = 17f
            setTextColor(0xFF0B1E3B.toInt())
        })

        panelInner.addView(toggleRow("કોલ પછી પોપ-અપ", Store.popupEnabled) { c ->
            Store.popupEnabled = c; Store.save(this); toast(if (c) "પોપ-અપ ચાલુ" else "પોપ-અપ બંધ")
        })
        panelInner.addView(toggleRow("ફોનમાં સેવ્ડ હોય તો પોપ-અપ નહીં", Store.skipIfInPhonebook) { c ->
            Store.skipIfInPhonebook = c; Store.save(this)
            toast(if (c) "ફોન-ડિરેક્ટરીના નંબર છોડશે" else "બધા નવા નંબર બતાવશે")
        })

        panelInner.addView(divider())

        panelInner.addView(drawerItem("બધા સંપર્ક", Store.contacts.size, currentDept == null) {
            currentDept = null; onDrawerSelect()
        })
        Store.departments.forEach { d ->
            val n = Store.contacts.count { it.dept == d }
            panelInner.addView(drawerItem(d, n, currentDept == d) { currentDept = d; onDrawerSelect() })
        }

        panelInner.addView(divider())
        panelInner.addView(drawerItem("\u2699\uFE0F ડિપાર્ટમેન્ટ મેનેજ કરો", -1, false) {
            drawer.closeDrawer(GravityCompat.START); manageDepts()
        })
        panelInner.addView(drawerItem("\u2B07\uFE0F CSV / Excel એક્સપોર્ટ", -1, false) {
            drawer.closeDrawer(GravityCompat.START); exportMenu()
        })
    }

    private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, pad(12), 0, pad(4))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
        return row
    }

    private fun onDrawerSelect() {
        titleView.text = "\uD83D\uDC8E " + (currentDept ?: "બધા સંપર્ક")
        drawer.closeDrawer(GravityCompat.START)
        refreshList()
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0xFFE1E7F1.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, pad(1)).apply {
            topMargin = pad(10); bottomMargin = pad(6)
        }
    }

    private fun drawerItem(label: String, count: Int, active: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad(10), pad(12), pad(10), pad(12))
            if (active) setBackgroundColor(0xFFE7EEFB.toInt())
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 15f
            setTextColor(if (active) 0xFF2563C9.toInt() else 0xFF12203A.toInt())
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        if (count >= 0) row.addView(TextView(this).apply {
            text = count.toString(); textSize = 12f; setTextColor(0xFF67779A.toInt())
        })
        return row
    }

    // ---------------- list ----------------
    private fun matches(c: Contact): Boolean {
        if (currentDept != null && c.dept != currentDept) return false
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return c.name.lowercase().contains(q) || c.phone.lowercase().contains(q) ||
                c.dept.lowercase().contains(q) || c.note.lowercase().contains(q)
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val items = Store.contacts.filter { matches(it) }.sortedBy { it.name.lowercase() }
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "\nકોઈ સંપર્ક મળ્યો નહીં."
                setTextColor(0xFF67779A.toInt()); gravity = Gravity.CENTER
                setPadding(pad(20), pad(40), pad(20), pad(20))
            })
            return
        }
        items.forEach { c ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(pad(12), pad(10), pad(12), pad(10))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad(8) }
                isClickable = true
                setOnClickListener { contactActions(c) }
            }
            val thumb = loadThumb(c.photo, pad(48))
            if (thumb != null) {
                card.addView(ImageView(this).apply {
                    setImageBitmap(thumb)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(pad(48), pad(48)).apply { marginEnd = pad(12) }
                })
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply { text = c.name; textSize = 16f; setTextColor(0xFF12203A.toInt()) })
            val sub = buildString { if (c.dept.isNotEmpty()) append("[${c.dept}]  "); append(c.phone) }
            col.addView(TextView(this).apply { text = sub; textSize = 13f; setTextColor(0xFF67779A.toInt()) })
            col.addView(TextView(this).apply {
                text = "\uD83D\uDD52 " + fmtDate(c.createdAt); textSize = 11f; setTextColor(0xFF9AA7BF.toInt())
            })
            if (c.note.isNotEmpty()) col.addView(TextView(this).apply {
                text = "\uD83D\uDCDD ${c.note}"; textSize = 12f; setTextColor(0xFFD9822B.toInt())
            })
            card.addView(col)
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
                    2 -> { Store.contacts.remove(c); Store.save(this); buildDrawer(); refreshList() }
                }
            }.show()
    }

    // ---------------- add / edit (with photo) ----------------
    private fun addOrEdit(existing: Contact?) {
        val cid = existing?.id ?: Store.newId()
        var tmpPhoto = existing?.photo ?: ""

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(20), pad(8), pad(20), 0)
        }

        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFFEEF2F8.toInt())
            layoutParams = LinearLayout.LayoutParams(pad(80), pad(80))
        }
        fun paint() {
            val b = loadThumb(tmpPhoto, pad(80))
            if (b != null) preview.setImageBitmap(b) else preview.setImageResource(android.R.drawable.ic_menu_camera)
        }
        paint()

        val photoBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad(6), 0, 0) }
        val addPhoto = Button(this).apply { text = "ફોટો ઉમેરો/બદલો"; textSize = 12f }
        val delPhoto = Button(this).apply { text = "દૂર કરો"; textSize = 12f }
        addPhoto.setOnClickListener {
            pendingPhoto = { uri ->
                val path = savePhoto(uri, cid)
                if (path.isNotEmpty()) { tmpPhoto = path; paint() } else toast("ફોટો સેવ ન થયો")
            }
            pickImage.launch("image/*")
        }
        delPhoto.setOnClickListener { tmpPhoto = ""; paint() }   // app-copy removed; gallery untouched
        photoBtns.addView(addPhoto); photoBtns.addView(delPhoto)

        val name = EditText(this).apply { hint = "નામ"; setText(existing?.name ?: "") }
        val phone = EditText(this).apply { hint = "ફોન નંબર"; setText(existing?.phone ?: "") }
        val note = EditText(this).apply { hint = "નોટ"; setText(existing?.note ?: "") }
        val spin = Spinner(this)
        val depts = listOf("— કોઈ નહીં —") + Store.departments
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, depts)
        existing?.let { spin.setSelection(depts.indexOf(it.dept).coerceAtLeast(0)) }

        box.addView(preview); box.addView(photoBtns)
        box.addView(name); box.addView(phone)
        box.addView(TextView(this).apply { text = "ડિપાર્ટમેન્ટ"; setPadding(0, pad(10), 0, 0) })
        box.addView(spin); box.addView(note)

        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "નવો સંપર્ક" else "એડિટ")
            .setView(scroll)
            .setPositiveButton("સેવ") { _, _ ->
                val nm = name.text.toString().trim()
                val ph = phone.text.toString().trim()
                if (nm.isEmpty() || ph.isEmpty()) { toast("નામ અને નંબર જરૂરી છે"); return@setPositiveButton }
                val dept = if (spin.selectedItemPosition == 0) "" else spin.selectedItem.toString()
                if (existing == null) {
                    Store.contacts.add(Contact(cid, nm, ph, dept, note.text.toString().trim(), tmpPhoto))
                } else {
                    existing.name = nm; existing.phone = ph; existing.dept = dept
                    existing.note = note.text.toString().trim(); existing.photo = tmpPhoto
                }
                Store.save(this); buildDrawer(); refreshList()
            }
            .setNegativeButton("રદ", null)
            .show()
    }

    // ---------------- photos ----------------
    private fun savePhoto(uri: Uri, id: String): String {
        return try {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            var sample = 1
            val maxDim = 1000
            while (opt.outWidth / sample > maxDim || opt.outHeight / sample > maxDim) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return ""
            val dir = File(filesDir, "photos").apply { mkdirs() }
            val out = File(dir, "$id.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            out.absolutePath
        } catch (e: Exception) { "" }
    }

    private fun loadThumb(path: String, size: Int): Bitmap? {
        if (path.isEmpty()) return null
        return try {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opt)
            if (opt.outWidth <= 0) return null
            var sample = 1
            while (opt.outWidth / sample > size * 2 || opt.outHeight / sample > size * 2) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, o2)
        } catch (e: Exception) { null }
    }

    // ---------------- date/time/day ----------------
    private fun fmtDate(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(c.time)
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.time)
        return "${guDays[c.get(Calendar.DAY_OF_WEEK) - 1]}, $d  $t"
    }

    // ---------------- CSV export ----------------
    private fun exportMenu() {
        val opts = mutableListOf("બધા સંપર્ક")
        opts.addAll(Store.departments)
        AlertDialog.Builder(this)
            .setTitle("CSV / Excel એક્સપોર્ટ")
            .setItems(opts.toTypedArray()) { _, i ->
                exportCsv(if (i == 0) null else opts[i])
            }.show()
    }

    private fun csv(s: String): String =
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun exportCsv(dept: String?) {
        val rows = Store.contacts.filter { dept == null || it.dept == dept }.sortedBy { it.name.lowercase() }
        if (rows.isEmpty()) { toast("કોઈ સંપર્ક નથી"); return }
        val sb = StringBuilder("\uFEFF")   // BOM so Excel reads Gujarati
        sb.append("Name,Phone,Department,Note,Date,Time,Day\n")
        rows.forEach { c ->
            val cal = Calendar.getInstance().apply { timeInMillis = c.createdAt }
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
            val day = guDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            sb.append(listOf(c.name, c.phone, c.dept, c.note, date, time, day)
                .joinToString(",") { csv(it) }).append("\n")
        }
        try {
            val safe = (dept ?: "All").replace(Regex("[^A-Za-z0-9]"), "_")
            val f = File(cacheDir, "DiamondDirectory_$safe.csv")
            f.writeText(sb.toString())
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "CSV એક્સપોર્ટ / શેર કરો"))
        } catch (e: Exception) { toast("એક્સપોર્ટમાં તકલીફ") }
    }

    // ---------------- departments ----------------
    private fun manageDepts() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad(16), pad(8), pad(16), 0) }
        val addRow = LinearLayout(this)
        val newName = EditText(this).apply { hint = "નવું ડિપાર્ટમેન્ટ"; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
        val addBtn = Button(this).apply { text = "ઉમેરો" }
        addRow.addView(newName); addRow.addView(addBtn); box.addView(addRow)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; box.addView(holder)

        fun redraw() {
            holder.removeAllViews()
            Store.departments.toList().forEach { d ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, pad(8), 0, pad(8))
                }
                val cnt = Store.contacts.count { it.dept == d }
                row.addView(TextView(this).apply { text = "$d  ($cnt)"; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
                row.addView(Button(this).apply { text = "\uD83D\uDDD1\uFE0F"; setOnClickListener { confirmDeleteDept(d) { redraw() } } })
                holder.addView(row)
            }
        }
        addBtn.setOnClickListener {
            val v = newName.text.toString().trim()
            if (v.isNotEmpty() && !Store.departments.contains(v)) {
                Store.departments.add(v); Store.save(this); newName.setText(""); redraw(); buildDrawer()
            }
        }
        redraw()
        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this).setTitle("ડિપાર્ટમેન્ટ મેનેજ કરો").setView(scroll)
            .setPositiveButton("બંધ કરો", null).show()
    }

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
                        if (currentDept == dept) { currentDept = null; titleView.text = "\uD83D\uDC8E બધા સંપર્ક" }
                        Store.save(this); buildDrawer(); refreshList(); after()
                    }.show()
            }.show()
    }

    // ---------------- permissions ----------------
    private fun requestPerms() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }

    private fun ensureOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("એક પરમિશન બાકી છે")
                .setMessage("કોલ પતે એટલે પોપ-અપ દેખાય એ માટે \"Display over other apps\" ચાલુ કરો.")
                .setPositiveButton("સેટિંગ ખોલો") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("પછી", null).show()
        }
    }
}
