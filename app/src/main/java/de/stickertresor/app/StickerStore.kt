package de.stickertresor.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Kernlogik: WhatsApp-Sticker finden, Duplikate erkennen und in Bilder/Sticker-Tresor sichern.
 */
object StickerStore {

    private const val PREFS = "tresor"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_LAST_BACKUP = "last_backup"
    private const val KEY_AUTO = "auto_backup"
    private const val HASH_FILE = "gesichert.txt"

    const val FOLDER_NAME = "Sticker-Tresor"
    val RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/" + FOLDER_NAME + "/"

    /** Wo WhatsApp seine Sticker ablegt – wird in der Ordnerauswahl direkt geöffnet. */
    val WHATSAPP_STICKER_DIR: Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
    )

    data class Source(val uri: Uri, val name: String)

    data class Result(val found: Int, val saved: Int, val alreadySaved: Int, val failed: Int)

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Einstellungen ----------

    fun saveTreeUri(ctx: Context, uri: Uri) {
        prefs(ctx).edit().putString(KEY_TREE, uri.toString()).apply()
    }

    /** Der freigegebene WhatsApp-Ordner, oder null wenn (noch) keine Freigabe besteht. */
    fun treeUri(ctx: Context): Uri? {
        val stored = prefs(ctx).getString(KEY_TREE, null) ?: return null
        val uri = Uri.parse(stored)
        val granted = ctx.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (granted) uri else null
    }

    fun lastBackup(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_BACKUP, 0L)

    fun isAutoBackup(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO, false)

    fun setAutoBackup(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    // ---------- Sticker im WhatsApp-Ordner finden ----------

    fun listSources(ctx: Context, tree: Uri): List<Source> {
        val out = mutableListOf<Source>()
        walk(ctx, tree, DocumentsContract.getTreeDocumentId(tree), out)
        return out
    }

    private fun walk(ctx: Context, tree: Uri, docId: String, out: MutableList<Source>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        ctx.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(ctx, tree, id, out)
                } else if (mime == "image/webp" || name.endsWith(".webp", ignoreCase = true)) {
                    out += Source(DocumentsContract.buildDocumentUriUsingTree(tree, id), name)
                }
            }
        }
    }

    // ---------- Sichern ----------

    fun backup(ctx: Context, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result {
        val tree = treeUri(ctx) ?: throw IllegalStateException("Kein WhatsApp-Ordner verbunden")
        val sources = listSources(ctx, tree)
        val known = loadHashes(ctx)
        var saved = 0
        var already = 0
        var failed = 0

        sources.forEachIndexed { index, src ->
            onProgress(index, sources.size)
            try {
                val bytes = ctx.contentResolver.openInputStream(src.uri)?.use { it.readBytes() }
                    ?: throw IOException("Datei nicht lesbar")
                val hash = sha256(bytes)
                if (hash in known) {
                    already++
                } else {
                    writeToGallery(ctx, "sticker_" + hash.take(16) + ".webp", bytes)
                    known += hash
                    appendHash(ctx, hash)
                    saved++
                }
            } catch (e: Exception) {
                failed++
            }
        }
        onProgress(sources.size, sources.size)
        prefs(ctx).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
        return Result(sources.size, saved, already, failed)
    }

    private fun writeToGallery(ctx: Context, fileName: String, bytes: ByteArray) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Speichern fehlgeschlagen")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("Speichern fehlgeschlagen")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    // ---------- Gesicherte Sticker anzeigen ----------

    fun listBackups(ctx: Context): List<Uri> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val out = mutableListOf<Uri>()
        ctx.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$RELATIVE_PATH%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                out += ContentUris.withAppendedId(collection, c.getLong(0))
            }
        }
        return out
    }

    /** Lädt einen Sticker als Drawable – animierte Sticker bleiben animiert. */
    fun decode(ctx: Context, uri: Uri, sizePx: Int): Drawable? = try {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            if (sizePx in 1 until w && h > 0) {
                decoder.setTargetSize(sizePx, (sizePx.toLong() * h / w).toInt().coerceAtLeast(1))
            }
        }
    } catch (e: Exception) {
        null
    }

    // ---------- Duplikat-Erkennung ----------

    private fun hashFile(ctx: Context) = File(ctx.filesDir, HASH_FILE)

    private fun loadHashes(ctx: Context): MutableSet<String> {
        val f = hashFile(ctx)
        if (!f.exists()) return mutableSetOf()
        return f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
    }

    private fun appendHash(ctx: Context, hash: String) {
        hashFile(ctx).appendText(hash + "\n")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
