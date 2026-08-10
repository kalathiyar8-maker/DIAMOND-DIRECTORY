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
    var photo: String = "",                       // internal file path ("" = none)
    val createdAt: Long = System.currentTimeMillis()  // auto date/time/day
)

/** Local storage backed by SharedPreferences (JSON). No external DB needed. */
object Store {
    private const val PREF = "dd_store"
    private const val KEY = "data"

    val contacts = mutableListOf<Contact>()
    val departments = mutableListOf<String>()
    var popupEnabled = true            // call-end popup ON/OFF
    var skipIfInPhonebook = true       // no popup if number already in phone contacts
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, null)
        if (raw == null) {
            departments.addAll(
                listOf("Galaxy Scanning", "Doping", "Data Entry", "QC",
                    "Polish", "Sarin/Laser", "Manager", "Skin")
            )
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
                contacts.add(
                    Contact(
                        o.getString("id"), o.getString("name"), o.getString("phone"),
                        o.optString("dept", ""), o.optString("note", ""),
                        o.optString("photo", ""),
                        o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
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
            ca.put(
                JSONObject().put("id", c.id).put("name", c.name)
                    .put("phone", c.phone).put("dept", c.dept).put("note", c.note)
                    .put("photo", c.photo).put("createdAt", c.createdAt)
            )
        }
        root.put("departments", da).put("contacts", ca)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, root.toString()).apply()
    }

    private fun norm(p: String) = p.filter { it.isDigit() }
    fun exists(phone: String): Boolean {
        val n = norm(phone)
        return n.isNotEmpty() && contacts.any { norm(it.phone) == n }
    }
    fun newId() = System.currentTimeMillis().toString(36) + (0..9999).random()
}
