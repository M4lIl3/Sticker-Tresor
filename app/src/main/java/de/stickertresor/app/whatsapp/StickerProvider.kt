package de.stickertresor.app.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import de.stickertresor.app.data.Library
import de.stickertresor.app.data.LibraryState
import de.stickertresor.app.data.Pack
import java.io.File

/**
 * Stellt die Packs nach dem Schema bereit, das WhatsApp für Sticker-Apps erwartet
 * (metadata, stickers, stickers_asset). WhatsApp liest nur – Schreiben ist nicht vorgesehen.
 */
class StickerProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(WhatsAppPacks.AUTHORITY, "metadata", METADATA)
        addURI(WhatsAppPacks.AUTHORITY, "metadata/*", METADATA_SINGLE)
        addURI(WhatsAppPacks.AUTHORITY, "stickers/*", STICKERS)
        addURI(WhatsAppPacks.AUTHORITY, "stickers_asset/*/*", ASSET)
    }

    override fun onCreate(): Boolean = true

    private fun state(): LibraryState = Library.get(context!!).state.value

    /** Nur Packs, die alle Regeln erfüllen, werden WhatsApp angeboten. */
    private fun readyPacks(s: LibraryState): List<Pack> =
        s.packs.filter { WhatsAppPacks.problems(s, it).isEmpty() }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val s = state()
        val cursor = when (matcher.match(uri)) {
            METADATA -> packCursor(s, readyPacks(s))
            METADATA_SINGLE -> packCursor(s, readyPacks(s).filter { it.id == uri.lastPathSegment })
            STICKERS -> stickerCursor(s, readyPacks(s).firstOrNull { it.id == uri.lastPathSegment })
            else -> throw IllegalArgumentException("Unbekannte URI: $uri")
        }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    private fun packCursor(s: LibraryState, packs: List<Pack>): Cursor {
        val c = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack",
            )
        )
        packs.forEach { p ->
            c.addRow(
                arrayOf<Any>(
                    p.id,
                    p.name,
                    WhatsAppPacks.PUBLISHER,
                    WhatsAppPacks.trayFileName(p),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    p.version.toString(),
                    0,
                    if (WhatsAppPacks.isAnimated(s, p)) 1 else 0,
                )
            )
        }
        return c
    }

    private fun stickerCursor(s: LibraryState, pack: Pack?): Cursor {
        val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
        pack?.stickerIds?.mapNotNull { s.stickerById[it] }?.forEach { st ->
            val emojis = st.emojis.ifEmpty { listOf(WhatsAppPacks.DEFAULT_EMOJI) }
            val names = st.categories.mapNotNull { id -> s.categories.firstOrNull { it.id == id }?.name }
            c.addRow(arrayOf<Any>("${st.id}.webp", emojis.joinToString(","), names.joinToString(", ").ifEmpty { "Sticker" }))
        }
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != ASSET) return null
        val segments = uri.pathSegments
        if (segments.size != 3) return null
        val packId = segments[1]
        val fileName = segments[2]
        val s = state()
        val pack = s.packs.firstOrNull { it.id == packId } ?: return null
        val ctx = context!!
        val file: File = when {
            fileName == WhatsAppPacks.trayFileName(pack) -> WhatsAppPacks.ensureTray(ctx, pack)
            fileName.endsWith(".webp") && fileName.removeSuffix(".webp") in pack.stickerIds ->
                Library.get(ctx).stickerFile(fileName.removeSuffix(".webp"))
            else -> return null
        }
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        METADATA -> "vnd.android.cursor.dir/vnd.${WhatsAppPacks.AUTHORITY}.metadata"
        METADATA_SINGLE -> "vnd.android.cursor.item/vnd.${WhatsAppPacks.AUTHORITY}.metadata"
        STICKERS -> "vnd.android.cursor.dir/vnd.${WhatsAppPacks.AUTHORITY}.stickers"
        ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> throw IllegalArgumentException("Unbekannte URI: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Nicht unterstützt")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Nicht unterstützt")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Nicht unterstützt")

    private companion object {
        const val METADATA = 1
        const val METADATA_SINGLE = 2
        const val STICKERS = 3
        const val ASSET = 4
    }
}
