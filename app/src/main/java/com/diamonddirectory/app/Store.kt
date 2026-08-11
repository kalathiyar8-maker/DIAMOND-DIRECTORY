package com.diamonddirectory.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Contact(
    val id: String,
    var name: String,
    var phone: String,
    var dept: String,
    var note: String,
    var photo: String = "",
    var aadhaarFront: String = "",
    var aadhaarBack: String = "",
    var category: String = "neutral",   // good | medium | bad | neutral
    var usage: Int = 0,                  // used more -> rises to top
    var popupOn: Boolean = true,         // show info popup for this contact
    val createdAt: Long = System.currentTimeMillis()
)

data class Note(val id: String, var text: String, val createdAt: Long)

/** Local storage backed by SharedPreferences (JSON). */
object Store {
    private const val PREF = "dd_store"
    private const val KEY = "data"

    val contacts = mutableListOf<Contact>()
    val departments = mutableListOf<String>()
    val notes = mutableListOf<Note>()
    var popupEnabled = true
    var skipIfInPhonebook = true
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw == null) {
            departments.addAll(listOf("Galaxy Scanning", "Doping", "Data Entry", "QC",
                "Polish", "Sarin/Laser", "Manager", "Skin"))
            return
        }
        try {
            val root = JSONObject(raw)
            popupEnabled = root.optBoolean("popupEnabled", true)
            skipIfInPhonebook = root.optBoolean("skipIfInPhonebook", true)
            val da = root.getJSONArray("departments")
            for (i in 0 until da.length()) departments.add(da.getString(i))
            val ca = root.getJSONArray("contacts")
            for (i in 0 until ca.length()) {
                val o = ca.getJSONObject(i)
                contacts.add(Contact(
                    o.getString("id"), o.getString("name"), o.getString("phone"),
                    o.optString("dept", ""), o.optString("note", ""),
                    o.optString("photo", ""), o.optString("aadhaarFront", ""),
                    o.optString("aadhaarBack", ""), o.optString("category", "neutral"),
                    o.optInt("usage", 0), o.optBoolean("popupOn", true),
                    o.optLong("createdAt", System.currentTimeMillis())
                ))
            }
            val na = root.optJSONArray("notes")
            if (na != null) for (i in 0 until na.length()) {
                val o = na.getJSONObject(i)
                notes.add(Note(o.getString("id"), o.getString("text"), o.optLong("createdAt", 0)))
            }
        } catch (_: Exception) { }
    }

    fun save(ctx: Context) {
        val root = JSONObject()
        root.put("popupEnabled", popupEnabled)
        root.put("skipIfInPhonebook", skipIfInPhonebook)
        val da = JSONArray(); departments.forEach { da.put(it) }
        val ca = JSONArray()
        contacts.forEach { c ->
            ca.put(JSONObject().put("id", c.id).put("name", c.name).put("phone", c.phone)
                .put("dept", c.dept).put("note", c.note).put("photo", c.photo)
                .put("aadhaarFront", c.aadhaarFront).put("aadhaarBack", c.aadhaarBack)
                .put("category", c.category).put("usage", c.usage)
                .put("popupOn", c.popupOn).put("createdAt", c.createdAt))
        }
        val na = JSONArray()
        notes.forEach { n -> na.put(JSONObject().put("id", n.id).put("text", n.text).put("createdAt", n.createdAt)) }
        root.put("departments", da).put("contacts", ca).put("notes", na)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }

    private fun norm(p: String) = p.filter { it.isDigit() }
    fun exists(phone: String): Boolean = findByPhone(phone) != null
    fun findByPhone(phone: String): Contact? {
        val n = norm(phone); if (n.isEmpty()) return null
        val tail = if (n.length >= 10) n.takeLast(10) else n
        return contacts.firstOrNull {
            val cn = norm(it.phone)
            cn == n || (cn.length >= 10 && cn.takeLast(10) == tail)
        }
    }
    fun newId() = System.currentTimeMillis().toString(36) + (0..9999).random()
}
