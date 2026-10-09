package de.stickertresor.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.IOException
import java.security.MessageDigest

/** Sichert Sticker aus dem WhatsApp-Ordner in die Bibliothek und nach Bilder/Sticker-Tresor. */
object Backup {

    private const val PREFS = "tresor"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_LAST_BACKUP = "last_backup"
    private const val KEY_AUTO = "auto_backup"
    private const val KEY_MIGRATED = "migrated_v2"

    val RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/Sticker-Tresor/"

    /** Wo WhatsApp seine Sticker ablegt – wird in der Ordnerauswahl direkt geöffnet. */
    val WHATSAPP_STICKER_DIR: Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
    )

    data class Result(val found: Int, val saved: Int, val alreadySaved: Int, val failed: Int)

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Einstellungen ----------

    fun saveTreeUri(ctx: Context, uri: Uri) {
        prefs(ctx).edit().putString(KEY_TREE, uri.toString()).apply()
    }

    fun treeUri(ctx: Context): Uri? {
        val stored = prefs(ctx).getString(KEY_TREE, null) ?: return null
        val uri = Uri.parse(stored)
        val granted = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (granted) uri else null
    }

    fun lastBackup(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_BACKUP, 0L)

    fun isAutoBackup(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO, false)

    fun setAutoBackup(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    // ---------- Sichern ----------

    fun run(ctx: Context, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result {
        val tree = treeUri(ctx) ?: throw IllegalStateException("Kein WhatsApp-Ordner verbunden")
        val library = Library.get(ctx)
        val sources = listSources(ctx, tree)
        val known = library.state.value.stickers.map { it.id }.toMutableSet()
        val ignored = library.state.value.ignored
        val fresh = mutableListOf<Sticker>()
        var already = 0
        var failed = 0

        sources.forEachIndexed { index, src ->
            onProgress(index, sources.size)
            try {
                val bytes = ctx.contentResolver.openInputStream(src)?.use { it.readBytes() }
                    ?: throw IOException("Datei nicht lesbar")
                val id = idFor(bytes)
                if (id in known || id in ignored) {
                    already++
                } else {
                    fresh += store(ctx, library, id, bytes, galleryUri = null, writeGallery = true)
                    known += id
                    // Zwischendurch speichern, damit bei einem Abbruch nichts verloren geht.
                    if (fresh.size >= 25) {
                        library.addStickers(fresh.toList())
                        fresh.clear()
                    }
                }
            } catch (e: Exception) {
                failed++
            }
        }
        library.addStickers(fresh)
        onProgress(sources.size, sources.size)
        prefs(ctx).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
        return Result(sources.size, sources.size - already - failed, already, failed)
    }

    private fun store(
        ctx: Context,
        library: Library,
        id: String,
        bytes: ByteArray,
        galleryUri: String?,
        writeGallery: Boolean,
    ): Sticker {
        val file = library.stickerFile(id)
        if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        val gallery = galleryUri ?: if (writeGallery) {
            try {
                writeToGallery(ctx, "sticker_${id.take(16)}.webp", bytes).toString()
            } catch (e: Exception) {
                null
            }
        } else null
        val (w, h) = WebpInfo.size(bytes)
        return Sticker(
            id = id,
            animated = WebpInfo.isAnimated(bytes),
            addedAt = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong(),
            width = w,
            height = h,
            galleryUri = gallery,
        )
    }

    /** Übernimmt Sticker, die Version 1 der App schon in die Galerie gesichert hat. */
    fun migrateFromV1(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_MIGRATED, false)) return
        val library = Library.get(ctx)
        val known = library.state.value.stickerById.keys
        val imported = mutableListOf<Sticker>()
        for (uri in listGalleryCopies(ctx)) {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                val id = idFor(bytes)
                if (id in known || imported.any { it.id == id }) continue
                imported += store(ctx, library, id, bytes, galleryUri = uri.toString(), writeGallery = false)
            } catch (_: Exception) {
            }
        }
        library.addStickers(imported)
        p.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    // ---------- Hilfsfunktionen ----------

    private fun listSources(ctx: Context, tree: Uri): List<Uri> {
        val out = mutableListOf<Uri>()
        walk(ctx, tree, DocumentsContract.getTreeDocumentId(tree), out)
        return out
    }

    private fun walk(ctx: Context, tree: Uri, docId: String, out: MutableList<Uri>) {
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
                    out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
        }
    }

    private fun writeToGallery(ctx: Context, fileName: String, bytes: ByteArray): Uri {
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
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Speichern fehlgeschlagen")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun listGalleryCopies(ctx: Context): List<Uri> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val out = mutableListOf<Uri>()
        try {
            ctx.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("$RELATIVE_PATH%"),
                null
            )?.use { c ->
                while (c.moveToNext()) out += ContentUris.withAppendedId(collection, c.getLong(0))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun idFor(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(24)
}

object WebpInfo {
    fun isAnimated(b: ByteArray): Boolean {
        if (b.size < 21) return false
        return tag(b, 0) == "RIFF" && tag(b, 8) == "WEBP" && tag(b, 12) == "VP8X" &&
            (b[20].toInt() and 0x02) != 0
    }

    fun size(b: ByteArray): Pair<Int, Int> {
        if (b.size >= 30 && tag(b, 0) == "RIFF" && tag(b, 8) == "WEBP" && tag(b, 12) == "VP8X") {
            val w = 1 + u24(b, 24)
            val h = 1 + u24(b, 27)
            return w to h
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        return o.outWidth to o.outHeight
    }

    private fun tag(b: ByteArray, at: Int) = String(b, at, 4, Charsets.US_ASCII)

    private fun u24(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16)
}
