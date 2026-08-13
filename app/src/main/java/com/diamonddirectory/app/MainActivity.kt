package com.diamonddirectory.app

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
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
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    private var cameraUri: Uri? = null
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val cb = pendingPhoto; pendingPhoto = null
        val u = cameraUri; cameraUri = null
        if (ok && u != null && cb != null) cb(u)
    }
    private val restorePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) restoreFrom(uri)
    }

    private val guDays = arrayOf("રવિવાર","સોમવાર","મંગળવાર","બુધવાર","ગુરુવાર","શુક્રવાર","શનિવાર")
    private val catKeys = listOf("neutral", "good", "medium", "bad")
    private val catLabels = listOf("સામાન્ય (સફેદ)", "સારો (લીલો)", "મધ્યમ (પીળો)", "ખરાબ (લાલ)")

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

    override fun onResume() { super.onResume(); buildDrawer(); refreshList() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START)
        else super.onBackPressed()
    }

    // ============ colours / helpers ============
    private fun catText(cat: String) = when (cat) {
        "good" -> 0xFF16A34A; "medium" -> 0xFFB27A00; "bad" -> 0xFFDC2626; else -> 0xFF12203A
    }.toInt()
    private fun catStrip(cat: String) = when (cat) {
        "good" -> 0xFF16A34A; "medium" -> 0xFFF2C200; "bad" -> 0xFFDC2626; else -> 0xFFDDE4EF
    }.toInt()

    private fun roundedBg(fill: Int, radius: Float, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = radius
            if (stroke != null) setStroke(pad(1), stroke)
        }

    // ============ UI shell ============
    private fun buildUi() {
        drawer = DrawerLayout(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFEDF1F7.toInt())
        }

        // header (simple gradient bar)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF0B1E3B.toInt(), 0xFF16345F.toInt())
            )
            setPadding(pad(8), pad(12), pad(12), pad(12))
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menuBtn = TextView(this).apply {
            text = "\u2630"; setTextColor(0xFFFFFFFF.toInt()); textSize = 22f
            setPadding(pad(8), pad(2), pad(10), pad(2)); isClickable = true
            setOnClickListener { drawer.openDrawer(GravityCompat.START) }
        }
        titleView = TextView(this).apply {
            text = "\uD83D\uDC8E Diamond Directory"; setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "+ નવો"; setTextColor(0xFF0B1E3B.toInt()); textSize = 13f
            background = roundedBg(0xFF7DD3FC.toInt(), pad(18).toFloat())
            setPadding(pad(14), pad(8), pad(14), pad(8)); isClickable = true
            setOnClickListener { addOrEdit(null) }
        }
        bar.addView(menuBtn); bar.addView(titleView); bar.addView(addBtn)
        header.addView(bar)
        header.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF38BDF8.toInt(), 0xFF2563C9.toInt(), 0xFF7DD3FC.toInt()))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, pad(3)).apply { topMargin = pad(12) }
        })
        content.addView(header)

        searchInput = EditText(this).apply {
            hint = "\uD83D\uDD0D શોધો…"; setSingleLine(true)
            background = roundedBg(0xFFFFFFFF.toInt(), pad(12).toFloat(), 0xFFE1E7F1.toInt())
            setPadding(pad(14), pad(11), pad(14), pad(11))
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
            orientation = LinearLayout.VERTICAL; setPadding(pad(10), pad(6), pad(10), pad(24))
        }
        val scroll = ScrollView(this); scroll.isVerticalScrollBarEnabled = false
        scroll.addView(listBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        drawer.addView(content, DrawerLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        panelInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad(16), pad(20), pad(16), pad(24))
        }
        val panel = ScrollView(this).apply { setBackgroundColor(0xFFFFFFFF.toInt()) }
        panel.addView(panelInner)
        val plp = DrawerLayout.LayoutParams(pad(292), MATCH_PARENT); plp.gravity = Gravity.START
        drawer.addView(panel, plp)

        setContentView(drawer)
    }

    // ============ side menu ============
    private fun buildDrawer() {
        panelInner.removeAllViews()
        panelInner.addView(TextView(this).apply {
            text = "\uD83D\uDC8E Diamond Directory"; textSize = 17f; setTextColor(0xFF0B1E3B.toInt())
        })
        panelInner.addView(toggleRow("કોલ પછી પોપ-અપ", Store.popupEnabled) { c ->
            Store.popupEnabled = c; Store.save(this); toast(if (c) "ચાલુ" else "બંધ")
        })
        panelInner.addView(toggleRow("ફોનમાં સેવ્ડ હોય તો પોપ-અપ નહીં", Store.skipIfInPhonebook) { c ->
            Store.skipIfInPhonebook = c; Store.save(this)
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
        panelInner.addView(drawerItem("\uD83D\uDCDD નોટ્સ", Store.notes.size, false) {
            drawer.closeDrawer(GravityCompat.START); openNotes()
        })
        panelInner.addView(drawerItem("\u2699\uFE0F ડિપાર્ટમેન્ટ મેનેજ કરો", -1, false) {
            drawer.closeDrawer(GravityCompat.START); manageDepts()
        })
        panelInner.addView(drawerItem("\u2B07\uFE0F CSV / Excel + ફોટો એક્સપોર્ટ", -1, false) {
            drawer.closeDrawer(GravityCompat.START); exportMenu()
        })
        panelInner.addView(drawerItem("\uD83D\uDCE4 એપ (APK) શેર કરો", -1, false) {
            drawer.closeDrawer(GravityCompat.START); shareApk()
        })
        panelInner.addView(divider())
        panelInner.addView(drawerItem("\uD83D\uDCBE બેકઅપ (બધો ડેટા સેવ)", -1, false) {
            drawer.closeDrawer(GravityCompat.START); backupAll()
        })
        panelInner.addView(drawerItem("\u267B\uFE0F રિસ્ટોર (પાછું લાવો)", -1, false) {
            drawer.closeDrawer(GravityCompat.START); confirmRestore()
        })
    }

    private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad(12), 0, pad(4))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, c -> onChange(c) } })
        return row
    }

    private fun onDrawerSelect() {
        titleView.text = "\uD83D\uDC8E " + (currentDept ?: "Diamond Directory")
        drawer.closeDrawer(GravityCompat.START); refreshList()
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0xFFE1E7F1.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, pad(1)).apply { topMargin = pad(10); bottomMargin = pad(6) }
    }

    private fun drawerItem(label: String, count: Int, active: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(pad(10), pad(12), pad(10), pad(12))
            if (active) background = roundedBg(0xFFE7EEFB.toInt(), pad(10).toFloat())
            isClickable = true; setOnClickListener { onClick() }
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

    // ============ list ============
    private fun matches(c: Contact): Boolean {
        if (currentDept != null && c.dept != currentDept) return false
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return c.name.lowercase().contains(q) || c.phone.lowercase().contains(q) ||
                c.dept.lowercase().contains(q) || c.note.lowercase().contains(q)
    }

    private fun refreshList() {
        listBox.removeAllViews()
        // most-used first, then name
        val items = Store.contacts.filter { matches(it) }
            .sortedWith(compareByDescending<Contact> { it.usage }.thenBy { it.name.lowercase() })
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
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(0xFFFFFFFF.toInt(), pad(14).toFloat(), 0xFFE6ECF5.toInt())
                setPadding(0, 0, pad(12), 0)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad(8) }
                isClickable = true; setOnClickListener { contactActions(c) }
            }
            // category colour strip
            card.addView(View(this).apply {
                setBackgroundColor(catStrip(c.category))
                layoutParams = LinearLayout.LayoutParams(pad(6), pad(64)).apply { marginEnd = pad(10) }
            })
            card.addView(avatarView(c, pad(46)))
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                setPadding(0, pad(10), 0, pad(10))
            }
            col.addView(TextView(this).apply {
                text = c.name; textSize = 16.5f; setTextColor(catText(c.category))
                if (c.category != "neutral") setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            val sub = buildString { if (c.dept.isNotEmpty()) append("[${c.dept}]  "); append(c.phone) }
            col.addView(TextView(this).apply { text = sub; textSize = 13f; setTextColor(0xFF67779A.toInt()) })
            if (c.aadhaarFront.isNotEmpty() || c.aadhaarBack.isNotEmpty()) col.addView(TextView(this).apply {
                text = "\uD83E\uDEAA આધાર"; textSize = 11f; setTextColor(0xFF0E9F6E.toInt())
            })
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
            .setItems(arrayOf("કોલ કરો", "એડિટ કરો", "\u23F0 કોલ રિમાઇન્ડર", "ડિલીટ કરો")) { _, w ->
                when (w) {
                    0 -> { c.usage += 1; Store.save(this); startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone))); refreshList() }
                    1 -> addOrEdit(c)
                    2 -> scheduleReminder(c)
                    3 -> { Store.contacts.remove(c); Store.save(this); buildDrawer(); refreshList() }
                }
            }.show()
    }

    // ============ add / edit ============
    private fun addOrEdit(existing: Contact?) {
        val cid = existing?.id ?: Store.newId()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad(20), pad(8), pad(20), 0) }

        fun imageSlot(label: String, initial: String, suffix: String): () -> String {
            var path = initial
            box.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(0xFF67779A.toInt()); setPadding(0, pad(10), 0, pad(2)) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBg(0xFFEEF2F8.toInt(), pad(8).toFloat(), 0xFFE1E7F1.toInt())
                layoutParams = LinearLayout.LayoutParams(pad(52), pad(52)).apply { marginEnd = pad(10) }
            }
            fun paint() {
                val b = loadThumb(path, pad(52))
                if (b != null) preview.setImageBitmap(b) else preview.setImageResource(android.R.drawable.ic_menu_camera)
            }
            paint()
            preview.setOnClickListener { if (path.isNotEmpty()) openImage(path) }
            fun smallBtn(txt: String) = Button(this).apply {
                text = txt; textSize = 11f; minWidth = 0; minimumWidth = 0
                setPadding(pad(12), pad(6), pad(12), pad(6))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = pad(6) }
            }
            row.addView(preview)
            row.addView(smallBtn("ઉમેરો/બદલો").apply {
                setOnClickListener {
                    pendingPhoto = { uri -> val p = savePhoto(uri, cid, suffix); if (p.isNotEmpty()) { path = p; paint() } else toast("સેવ ન થયું") }
                    chooseSource()
                }
            })
            row.addView(smallBtn("દૂર").apply { setOnClickListener { path = ""; paint() } })
            box.addView(row)
            return { path }
        }

        val getPhoto = imageSlot("કારીગરનો ફોટો", existing?.photo ?: "", "")
        val getFront = imageSlot("આધાર કાર્ડ - આગળ", existing?.aadhaarFront ?: "", "_aadhaar_f")
        val getBack  = imageSlot("આધાર કાર્ડ - પાછળ", existing?.aadhaarBack ?: "", "_aadhaar_b")

        val name = EditText(this).apply { hint = "નામ"; setText(existing?.name ?: "") }
        val phone = EditText(this).apply { hint = "ફોન નંબર"; setText(existing?.phone ?: "") }
        val note = EditText(this).apply { hint = "નોટ"; setText(existing?.note ?: "") }
        val spin = Spinner(this)
        val depts = listOf("— કોઈ નહીં —") + Store.departments
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, depts)
        existing?.let { spin.setSelection(depts.indexOf(it.dept).coerceAtLeast(0)) }

        val catSpin = Spinner(this)
        catSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, catLabels)
        catSpin.setSelection(catKeys.indexOf(existing?.category ?: "neutral").coerceAtLeast(0))

        box.addView(TextView(this).apply { text = "નામ"; setPadding(0, pad(12), 0, 0) })
        box.addView(name); box.addView(phone)
        box.addView(TextView(this).apply { text = "ડિપાર્ટમેન્ટ"; setPadding(0, pad(10), 0, 0) })
        box.addView(spin)
        box.addView(TextView(this).apply { text = "કેટેગરી (લિસ્ટમાં રંગ)"; setPadding(0, pad(10), 0, 0) })
        box.addView(catSpin)
        box.addView(note)

        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "નવો સંપર્ક" else "એડિટ")
            .setView(scroll)
            .setPositiveButton("સેવ") { _, _ ->
                val nm = name.text.toString().trim(); val ph = phone.text.toString().trim()
                if (nm.isEmpty() || ph.isEmpty()) { toast("નામ અને નંબર જરૂરી છે"); return@setPositiveButton }
                val dept = if (spin.selectedItemPosition == 0) "" else spin.selectedItem.toString()
                val cat = catKeys[catSpin.selectedItemPosition]
                if (existing == null) {
                    Store.contacts.add(Contact(cid, nm, ph, dept, note.text.toString().trim(),
                        getPhoto(), getFront(), getBack(), cat))
                } else {
                    existing.name = nm; existing.phone = ph; existing.dept = dept
                    existing.note = note.text.toString().trim(); existing.category = cat
                    existing.photo = getPhoto(); existing.aadhaarFront = getFront(); existing.aadhaarBack = getBack()
                }
                Store.save(this); buildDrawer(); refreshList()
            }
            .setNegativeButton("રદ", null).show()
    }

    // ============ camera / gallery ============
    private fun chooseSource() {
        AlertDialog.Builder(this)
            .setTitle("ફોટો ક્યાંથી લેવો?")
            .setItems(arrayOf("\uD83D\uDCF7 કેમેરા", "\uD83D\uDDBC\uFE0F ગેલેરી")) { _, w ->
                if (w == 0) launchCamera() else pickImage.launch("image/*")
            }
            .setOnCancelListener { pendingPhoto = null }.show()
    }
    private fun launchCamera() {
        try {
            val f = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            cameraUri = uri
            val cam = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            packageManager.queryIntentActivities(cam, PackageManager.MATCH_DEFAULT_ONLY).forEach {
                grantUriPermission(it.activityInfo.packageName, uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            takePicture.launch(uri)
        } catch (e: Exception) { pendingPhoto = null; toast("કેમેરા ખૂલ્યો નહીં") }
    }

    // ============ photos ============
    private fun savePhoto(uri: Uri, id: String, suffix: String): String {
        return try {
            val dir = File(filesDir, "photos").apply { mkdirs() }
            val out = File(dir, "$id$suffix.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return ""
            out.absolutePath
        } catch (e: Exception) { "" }
    }
    private fun openImage(path: String) {
        if (path.isEmpty()) return
        startActivity(Intent(this, ImageViewerActivity::class.java).putExtra("path", path))
    }
    private val avatarColors = intArrayOf(
        0xFFE57373.toInt(), 0xFF64B5F6.toInt(), 0xFF81C784.toInt(), 0xFFFFB74D.toInt(),
        0xFFBA68C8.toInt(), 0xFF4DB6AC.toInt(), 0xFF7986CB.toInt(), 0xFFA1887F.toInt())
    private fun avatarColor(name: String) = avatarColors[Math.abs(name.hashCode()) % avatarColors.size]
    private fun avatarView(c: Contact, sizePx: Int): View {
        if (c.photo.isNotEmpty()) {
            val bmp = loadThumb(c.photo, sizePx)
            if (bmp != null) return ImageView(this).apply {
                val rbd = RoundedBitmapDrawableFactory.create(resources, bmp); rbd.isCircular = true
                setImageDrawable(rbd)
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = pad(12) }
                setOnClickListener { openImage(c.photo) }
            }
        }
        val initial = c.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        return TextView(this).apply {
            text = initial; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.42f)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(avatarColor(c.name)) }
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = pad(12) }
        }
    }
    private fun loadThumb(path: String, size: Int): Bitmap? {
        if (path.isEmpty()) return null
        return try {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opt)
            if (opt.outWidth <= 0) return null
            var sample = 1
            while (opt.outWidth / sample > size * 2 || opt.outHeight / sample > size * 2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) { null }
    }

    // ============ date ============
    private fun fmtDate(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val d = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(c.time)
        val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.time)
        return "${guDays[c.get(Calendar.DAY_OF_WEEK) - 1]}, $d  $t"
    }

    // ============ notes ============
    private fun openNotes() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad(16), pad(8), pad(16), 0) }
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val newBtn = Button(this).apply { text = "\u2795 નવી નોટ" }
        box.addView(newBtn); box.addView(holder)

        fun redraw() {
            holder.removeAllViews()
            val list = Store.notes.sortedByDescending { it.createdAt }
            if (list.isEmpty()) holder.addView(TextView(this).apply {
                text = "\nહજી કોઈ નોટ નથી."; setTextColor(0xFF67779A.toInt()); setPadding(0, pad(16), 0, 0)
            })
            list.forEach { n ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedBg(0xFFF3F6FB.toInt(), pad(10).toFloat())
                    setPadding(pad(12), pad(10), pad(12), pad(10))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = pad(8) }
                    isClickable = true
                }
                item.addView(TextView(this).apply { text = n.text; textSize = 15f; setTextColor(0xFF12203A.toInt()) })
                if (n.photos.isNotEmpty()) {
                    val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad(6), 0, 0) }
                    n.photos.take(4).forEach { p ->
                        val b = loadThumb(p, pad(48))
                        if (b != null) strip.addView(ImageView(this).apply {
                            setImageBitmap(b); scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = LinearLayout.LayoutParams(pad(48), pad(48)).apply { marginEnd = pad(6) }
                            setOnClickListener { openImage(p) }
                        })
                    }
                    item.addView(strip)
                }
                val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad(6), 0, 0) }
                bottom.addView(TextView(this).apply {
                    text = "\uD83D\uDD52 " + fmtDate(n.createdAt); textSize = 11f; setTextColor(0xFF9AA7BF.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                })
                bottom.addView(TextView(this).apply {
                    text = "\u270F\uFE0F"; setPadding(pad(8), 0, pad(8), 0); isClickable = true
                    setOnClickListener { noteEditor(n) { redraw() } }
                })
                bottom.addView(TextView(this).apply {
                    text = "\uD83D\uDDD1\uFE0F"; isClickable = true
                    setOnClickListener { Store.notes.remove(n); Store.save(this@MainActivity); redraw(); buildDrawer() }
                })
                item.addView(bottom)
                holder.addView(item)
            }
        }
        newBtn.setOnClickListener { noteEditor(null) { redraw() } }
        redraw()
        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this).setTitle("\uD83D\uDCDD નોટ્સ (તારીખ પ્રમાણે)").setView(scroll)
            .setPositiveButton("બંધ કરો", null).show()
    }

    /** New / edit note window with gallery + camera photos. */
    private fun noteEditor(existing: Note?, onDone: () -> Unit) {
        val nid = existing?.id ?: Store.newId()
        val photos = existing?.photos?.toMutableList() ?: mutableListOf()

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad(18), pad(8), pad(18), 0) }
        val input = EditText(this).apply { hint = "વિગત / નોટ લખો"; setText(existing?.text ?: ""); minLines = 3 }
        box.addView(input)

        box.addView(TextView(this).apply { text = "ફોટા"; textSize = 12f; setTextColor(0xFF67779A.toInt()); setPadding(0, pad(12), 0, pad(4)) })
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun paintStrip() {
            strip.removeAllViews()
            photos.forEach { p ->
                val cell = FrameLayout(this)
                val b = loadThumb(p, pad(60))
                cell.addView(ImageView(this).apply {
                    if (b != null) setImageBitmap(b) else setImageResource(android.R.drawable.ic_menu_report_image)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(pad(60), pad(60))
                    setOnClickListener { openImage(p) }
                })
                cell.addView(TextView(this).apply {
                    text = "\u2715"; setTextColor(0xFFFFFFFF.toInt()); textSize = 12f
                    setBackgroundColor(0xAA000000.toInt()); setPadding(pad(4), 0, pad(4), 0)
                    isClickable = true
                    setOnClickListener { photos.remove(p); paintStrip() }
                    layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END)
                })
                strip.addView(cell, LinearLayout.LayoutParams(pad(60), pad(60)).apply { marginEnd = pad(8) })
            }
        }
        paintStrip()
        val addPhoto = Button(this).apply {
            text = "\uD83D\uDCF7 / \uD83D\uDDBC\uFE0F ફોટો ઉમેરો"; textSize = 12f
            setOnClickListener {
                pendingPhoto = { uri ->
                    val p = savePhoto(uri, nid, "_note_" + System.currentTimeMillis())
                    if (p.isNotEmpty()) { photos.add(p); paintStrip() } else toast("સેવ ન થયું")
                }
                chooseSource()
            }
        }
        val strScroll = HorizontalScrollView(this); strScroll.addView(strip)
        box.addView(strScroll); box.addView(addPhoto)

        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "\uD83D\uDCDD નવી નોટ" else "નોટ એડિટ")
            .setView(scroll)
            .setPositiveButton("સેવ") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isEmpty() && photos.isEmpty()) { toast("કંઈક લખો કે ફોટો ઉમેરો"); return@setPositiveButton }
                if (existing == null) {
                    Store.notes.add(Note(nid, t, System.currentTimeMillis(), photos))
                } else {
                    existing.text = t; existing.photos.clear(); existing.photos.addAll(photos)
                }
                Store.save(this); buildDrawer(); onDone()
            }
            .setNegativeButton("રદ", null).show()
    }

    // ============ backup / restore ============
    private fun backupAll() {
        try {
            val zipFile = File(cacheDir, "DiamondDirectory_Backup.zip")
            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
            // 1) all data as json
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(Store.rawJson(this).toByteArray(Charsets.UTF_8)); zos.closeEntry()
            // 2) all image files
            val dir = File(filesDir, "photos")
            dir.listFiles()?.forEach { f ->
                zos.putNextEntry(ZipEntry("photos/" + f.name))
                f.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
            }
            zos.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", zipFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "બેકઅપ સેવ / શેર કરો"))
        } catch (e: Exception) { toast("બેકઅપ ન થયું") }
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
            .setTitle("રિસ્ટોર કરવું?")
            .setMessage("બેકઅપ ફાઈલ (DiamondDirectory_Backup.zip) પસંદ કરો. હાલનો ડેટા એનાથી બદલાઈ જશે.")
            .setNegativeButton("રદ", null)
            .setPositiveButton("ફાઈલ પસંદ કરો") { _, _ -> restorePicker.launch("application/zip") }
            .show()
    }

    private fun restoreFrom(uri: Uri) {
        try {
            val dir = File(filesDir, "photos").apply { mkdirs() }
            var json: String? = null
            java.util.zip.ZipInputStream(contentResolver.openInputStream(uri)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == "data.json") {
                        json = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (name.startsWith("photos/") && !e.isDirectory) {
                        val out = File(dir, name.substringAfterLast('/'))
                        FileOutputStream(out).use { o -> zis.copyTo(o) }
                    }
                    zis.closeEntry(); e = zis.nextEntry
                }
            }
            if (json != null) {
                Store.writeRaw(this, json!!)
                Store.reload(this)
                currentDept = null
                buildDrawer(); refreshList()
                toast("રિસ્ટોર થઈ ગયું ✓")
            } else toast("ખોટી બેકઅપ ફાઈલ")
        } catch (e: Exception) { toast("રિસ્ટોર ન થયું") }
    }

    // ============ reminder ============
    private fun scheduleReminder(c: Contact) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, day ->
            TimePickerDialog(this, { _, h, min ->
                val cal = Calendar.getInstance().apply { set(y, m, day, h, min, 0) }
                if (cal.timeInMillis <= System.currentTimeMillis()) { toast("ભૂતકાળનો સમય ન ચાલે"); return@TimePickerDialog }
                val i = Intent(this, ReminderReceiver::class.java)
                    .putExtra("name", c.name).putExtra("phone", c.phone)
                val pi = PendingIntent.getBroadcast(this, c.id.hashCode(), i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val am = getSystemService(ALARM_SERVICE) as AlarmManager
                try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
                catch (e: Exception) { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
                toast("રિમાઇન્ડર સેટ થયું ✓")
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ============ APK share ============
    private fun shareApk() {
        try {
            val src = File(applicationInfo.sourceDir)
            val out = File(cacheDir, "DiamondDirectory.apk")
            src.inputStream().use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "એપ (APK) શેર કરો"))
        } catch (e: Exception) { toast("શેર ન થયું") }
    }

    // ============ export (CSV + images -> ZIP) ============
    private fun exportMenu() {
        val opts = mutableListOf("બધા સંપર્ક"); opts.addAll(Store.departments)
        AlertDialog.Builder(this).setTitle("CSV / Excel + ફોટો એક્સપોર્ટ")
            .setItems(opts.toTypedArray()) { _, i -> exportZip(if (i == 0) null else opts[i]) }.show()
    }
    private fun csv(s: String): String =
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) "\"" + s.replace("\"", "\"\"") + "\"" else s
    private fun addToZip(zos: ZipOutputStream, path: String, entryName: String) {
        if (path.isEmpty() || entryName.isEmpty()) return
        val f = File(path); if (!f.exists()) return
        zos.putNextEntry(ZipEntry(entryName)); f.inputStream().use { it.copyTo(zos) }; zos.closeEntry()
    }
    private fun exportZip(dept: String?) {
        val rows = Store.contacts.filter { dept == null || it.dept == dept }.sortedBy { it.name.lowercase() }
        if (rows.isEmpty() && dept != null) { toast("કોઈ સંપર્ક નથી"); return }
        try {
            val safe = (dept ?: "All").replace(Regex("[^A-Za-z0-9]"), "_")
            val zipFile = File(cacheDir, "DiamondDirectory_$safe.zip")
            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))

            // ---- 1) CONTACTS ----
            val sb = StringBuilder("\uFEFF")
            sb.append("No,Name,Phone,Department,Category,Note,Date,Time,Day,PhotoFile,AadhaarFront,AadhaarBack\n")
            val catMap = mapOf("good" to "Good", "medium" to "Medium", "bad" to "Bad", "neutral" to "Neutral")
            rows.forEachIndexed { idx, c ->
                val cal = Calendar.getInstance().apply { timeInMillis = c.createdAt }
                val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                val day = guDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
                val base = "${idx + 1}_" + c.name.replace(Regex("[^A-Za-z0-9]"), "_").ifEmpty { "contact" }
                val pName = if (c.photo.isNotEmpty()) "$base.jpg" else ""
                val fName = if (c.aadhaarFront.isNotEmpty()) "${base}_aadhaar_front.jpg" else ""
                val bName = if (c.aadhaarBack.isNotEmpty()) "${base}_aadhaar_back.jpg" else ""
                sb.append(listOf((idx + 1).toString(), c.name, c.phone, c.dept,
                    catMap[c.category] ?: "Neutral", c.note, date, time, day, pName, fName, bName)
                    .joinToString(",") { csv(it) }).append("\n")
                addToZip(zos, c.photo, "gallery/contacts/$pName")
                addToZip(zos, c.aadhaarFront, "gallery/contacts/$fName")
                addToZip(zos, c.aadhaarBack, "gallery/contacts/$bName")
            }
            zos.putNextEntry(ZipEntry("contacts.csv")); zos.write(sb.toString().toByteArray(Charsets.UTF_8)); zos.closeEntry()

            // ---- 2) NOTES + note gallery (only in full "All" export) ----
            if (dept == null) {
                val nb = StringBuilder("\uFEFF")
                nb.append("No,Note,Date,Time,Day,PhotoFiles\n")
                Store.notes.sortedByDescending { it.createdAt }.forEachIndexed { idx, n ->
                    val cal = Calendar.getInstance().apply { timeInMillis = n.createdAt }
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                    val day = guDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    val names = n.photos.mapIndexed { j, p ->
                        val nm = "note${idx + 1}_${j + 1}.jpg"; addToZip(zos, p, "gallery/notes/$nm"); nm
                    }.joinToString(" | ")
                    nb.append(listOf((idx + 1).toString(), n.text, date, time, day, names)
                        .joinToString(",") { csv(it) }).append("\n")
                }
                zos.putNextEntry(ZipEntry("notes.csv")); zos.write(nb.toString().toByteArray(Charsets.UTF_8)); zos.closeEntry()
            }

            zos.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", zipFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "એક્સપોર્ટ / શેર કરો"))
        } catch (e: Exception) { toast("એક્સપોર્ટમાં તકલીફ") }
    }

    // ============ departments ============
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
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad(8), 0, pad(8)) }
                val cnt = Store.contacts.count { it.dept == d }
                row.addView(TextView(this).apply { text = "$d  ($cnt)"; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
                row.addView(Button(this).apply { text = "\uD83D\uDDD1\uFE0F"; setOnClickListener { confirmDeleteDept(d) { redraw() } } })
                holder.addView(row)
            }
        }
        addBtn.setOnClickListener {
            val v = newName.text.toString().trim()
            if (v.isNotEmpty() && !Store.departments.contains(v)) { Store.departments.add(v); Store.save(this); newName.setText(""); redraw(); buildDrawer() }
        }
        redraw()
        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this).setTitle("ડિપાર્ટમેન્ટ મેનેજ કરો").setView(scroll).setPositiveButton("બંધ કરો", null).show()
    }
    private fun confirmDeleteDept(dept: String, after: () -> Unit) {
        AlertDialog.Builder(this).setMessage("\"$dept\" ડિપાર્ટમેન્ટ ડિલીટ કરવું છે?")
            .setNegativeButton("ના", null)
            .setPositiveButton("હા") { _, _ ->
                val n = Store.contacts.count { it.dept == dept }
                AlertDialog.Builder(this).setMessage("ખરેખર ડિલીટ કરવું?\n$n સંપર્ક રહેશે પણ ડિપાર્ટમેન્ટ વગરના થઈ જશે.")
                    .setNegativeButton("ના", null)
                    .setPositiveButton("હા, ડિલીટ") { _, _ ->
                        Store.departments.remove(dept)
                        Store.contacts.forEach { if (it.dept == dept) it.dept = "" }
                        if (currentDept == dept) currentDept = null
                        Store.save(this); buildDrawer(); refreshList(); after()
                    }.show()
            }.show()
    }

    // ============ permissions ============
    private fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val need = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }
    private fun ensureOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("એક પરમિશન બાકી છે")
                .setMessage("કોલ પતે એટલે પોપ-અપ દેખાય એ માટે \"Display over other apps\" ચાલુ કરો.")
                .setPositiveButton("સેટિંગ ખોલો") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }.setNegativeButton("પછી", null).show()
        }
    }
}
