package com.hiremeter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors

/**
 * One file holding everything the driver would lose with the phone: rates, trips, payment details
 * and QR, settings and language. The trip in progress is not in it; it belongs to this phone.
 */
object Backup {
    const val APP = "HireMeter"
    /** The SharedPreferences files that are backed up, as they are. */
    private val PREFS = listOf("rates", "history", "pay", "settings", "lang", "signals")
    private fun qrFile(ctx: Context) = File(ctx.filesDir, "pay_qr")

    fun create(ctx: Context): String {
        val prefs = JSONObject()
        for (name in PREFS) prefs.put(name, prefsToJson(ctx.getSharedPreferences(name, Context.MODE_PRIVATE)))
        val qr = qrFile(ctx).takeIf { it.exists() }?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) } ?: ""
        return JSONObject()
            .put("app", APP)
            .put("version", 1)
            .put("savedAt", System.currentTimeMillis())
            .put("prefs", prefs)
            .put("qr", qr)
            .toString()
    }

    /** How many trips a backup holds, or null if it is not a HireMeter backup. */
    fun tripCount(text: String): Int? = runCatching {
        val root = JSONObject(text)
        require(root.optString("app") == APP)
        val history = root.getJSONObject("prefs").optJSONObject("history") ?: return@runCatching 0
        history.optJSONObject("trips")?.optString("v")?.let { JSONArray(it).length() } ?: 0
    }.getOrNull()

    /**
     * Replaces the app's data with the backup's and returns the number of trips. The whole file is
     * read first: if any of it is wrong, nothing on the phone changes.
     */
    fun restore(ctx: Context, text: String): Int {
        val root = JSONObject(text)
        require(root.optString("app") == APP) { "This file is not a HireMeter backup" }
        val prefs = root.getJSONObject("prefs")
        val parsed = PREFS.associateWith { name -> prefs.optJSONObject(name)?.let(::jsonToValues) }
        val qr = root.optString("qr").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.NO_WRAP) }

        for ((name, values) in parsed) {
            // A part missing from the backup (an older version) leaves that part as it is.
            values ?: continue
            val editor = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for ((key, v) in values) {
                when (v) {
                    is String -> editor.putString(key, v)
                    is Boolean -> editor.putBoolean(key, v)
                    is Float -> editor.putFloat(key, v)
                    is Long -> editor.putLong(key, v)
                    is Int -> editor.putInt(key, v)
                }
            }
            editor.commit()
        }
        if (qr != null) qrFile(ctx).writeBytes(qr) else qrFile(ctx).delete()
        return tripCount(text) ?: 0
    }

    /** Every value with its type, so it comes back exactly as it was. */
    private fun prefsToJson(p: SharedPreferences): JSONObject {
        val o = JSONObject()
        for ((key, v) in p.all) {
            val (type, value) = when (v) {
                is String -> "s" to v
                is Boolean -> "b" to v
                is Float -> "f" to v.toDouble()
                is Long -> "l" to v
                is Int -> "i" to v
                else -> continue // string sets: not used by the app
            }
            o.put(key, JSONObject().put("t", type).put("v", value))
        }
        return o
    }

    private fun jsonToValues(o: JSONObject): Map<String, Any> = o.keys().asSequence().associateWith { key ->
        val e = o.getJSONObject(key)
        when (e.getString("t")) {
            "s" -> e.getString("v")
            "b" -> e.getBoolean("v")
            "f" -> e.getDouble("v").toFloat()
            "l" -> e.getLong("v")
            "i" -> e.getInt("v")
            else -> error("Unknown value in the backup")
        }
    }
}

/**
 * Keeps one backup file in the driver's Google Drive up to date. The file is picked once with the
 * phone's own "save to" screen (choosing Drive), so no Google sign-in is needed; after that the app
 * rewrites the same file whenever the data has changed. Drive uploads it when there is internet.
 */
object DriveBackup {
    private const val TAG = "DriveBackup"
    private const val URI = "uri"
    private const val SAVED_AT = "savedAt"
    private const val SAVED_HASH = "hash"
    private const val ERROR = "error"
    const val FILE_NAME = "hiremeter-backup.json"

    /** One write at a time, off the main thread. */
    private val worker = Executors.newSingleThreadExecutor()

    /** Not in the backup itself: the link to Drive belongs to this phone. */
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drive_backup", Context.MODE_PRIVATE)

    data class State(val linked: Boolean, val savedAt: Long, val error: String)

    fun state(ctx: Context) = State(linkedUri(ctx) != null, prefs(ctx).getLong(SAVED_AT, 0L), prefs(ctx).getString(ERROR, "").orEmpty())

    /**
     * The linked file, or null. A link only counts while Android still lets the app write there:
     * on a new phone the saved link can come back without the permission, so it is dropped.
     */
    private fun linkedUri(ctx: Context): Uri? {
        val p = prefs(ctx)
        val uri = p.getString(URI, null)?.let(Uri::parse) ?: return null
        val allowed = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!allowed) {
            p.edit().remove(URI).remove(SAVED_HASH).apply()
            return null
        }
        return uri
    }

    /** Remembers the file picked on Drive and writes the first backup into it. Call off the main thread. */
    fun link(ctx: Context, uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs(ctx).edit().putString(URI, uri.toString()).remove(SAVED_HASH).apply()
        save(ctx, force = true)
    }

    /**
     * The backup already in a picked file, if it holds trips. Linking writes over the file, so on a
     * new phone an old backup is offered for restoring first instead of being replaced by an empty app.
     */
    fun existingBackup(ctx: Context, uri: Uri): String? = runCatching {
        read(ctx, uri).takeIf { (Backup.tripCount(it) ?: 0) > 0 }
    }.getOrNull()

    fun unlink(ctx: Context) {
        prefs(ctx).getString(URI, null)?.let { saved ->
            runCatching {
                ctx.contentResolver.releasePersistableUriPermission(
                    Uri.parse(saved), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs(ctx).edit().clear().apply()
    }

    /**
     * Writes the backup if anything changed since the last one (or always with [force]). Returns
     * false when no file is linked or nothing changed. Throws, and remembers the error for Settings,
     * if Drive could not be written.
     */
    fun save(ctx: Context, force: Boolean = false): Boolean {
        val uri = linkedUri(ctx) ?: return false
        // The time inside changes every backup, so it is left out of the comparison.
        val text = Backup.create(ctx)
        val hash = JSONObject(text).apply { remove("savedAt") }.toString().let { "${it.length}:${it.hashCode()}" }
        val p = prefs(ctx)
        if (!force && p.getString(SAVED_HASH, null) == hash) return false
        try {
            write(ctx, uri, text)
            // Read it back: a file left half old, half new would be a backup that cannot be restored.
            JSONObject(read(ctx, uri))
            p.edit().putString(SAVED_HASH, hash).putLong(SAVED_AT, System.currentTimeMillis()).remove(ERROR).apply()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Drive backup failed", e)
            p.edit().putString(ERROR, e.message ?: e.javaClass.simpleName).remove(SAVED_HASH).apply()
            throw e
        }
    }

    /** [save] in the background; [done] gets the error message, or null when it worked or there was nothing to do. */
    fun saveLater(ctx: Context, force: Boolean = false, done: (String?) -> Unit = {}) {
        val app = ctx.applicationContext
        worker.execute { done(runCatching { save(app, force) }.exceptionOrNull()?.let { it.message ?: "error" }) }
    }

    /** The backup text in the linked Drive file, for restoring. */
    fun readLinked(ctx: Context): String {
        val uri = linkedUri(ctx) ?: error("No Drive file is linked")
        return read(ctx, uri)
    }

    private fun write(ctx: Context, uri: Uri, text: String) {
        val resolver = ctx.contentResolver
        // "wt" empties the file first. Some providers only know "w", which Drive also treats as a full rewrite.
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            try {
                resolver.openOutputStream(uri, "w")
            } catch (missing: FileNotFoundException) {
                throw FileNotFoundException("The Drive file is missing. Turn on Drive backup again.")
            }
        } ?: error("Could not open the Drive file")
        stream.bufferedWriter().use { it.write(text) }
    }

    fun read(ctx: Context, uri: Uri): String =
        (ctx.contentResolver.openInputStream(uri) ?: error("Could not read the Drive file")).bufferedReader().use { it.readText() }
}
