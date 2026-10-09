package de.stickertresor.app.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import de.stickertresor.app.data.Library
import de.stickertresor.app.data.LibraryState
import de.stickertresor.app.data.Pack
import java.io.File

/** Regeln und Hilfen, um ein Pack als Sticker-Pack an WhatsApp zu übergeben. */
object WhatsAppPacks {

    const val AUTHORITY = "de.stickertresor.app.stickerprovider"
    const val PUBLISHER = "Sticker-Tresor"
    const val DEFAULT_EMOJI = "🙂"

    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    private const val MAX_STATIC_BYTES = 100 * 1024
    private const val MAX_ANIMATED_BYTES = 500 * 1024
    private const val STICKER_PX = 512
    private const val TRAY_PX = 96

    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun trayFileName(pack: Pack) = "tray_${pack.id}.png"

    fun isAnimated(state: LibraryState, pack: Pack): Boolean {
        val list = pack.stickerIds.mapNotNull { state.stickerById[it] }
        return list.isNotEmpty() && list.all { it.animated }
    }

    /** Leere Liste = Pack ist bereit für WhatsApp. */
    fun problems(state: LibraryState, pack: Pack): List<String> {
        val out = mutableListOf<String>()
        val stickers = pack.stickerIds.mapNotNull { state.stickerById[it] }
        if (pack.name.isBlank()) out += "Das Pack braucht einen Namen."
        if (stickers.size < MIN_STICKERS) {
            out += "Mindestens $MIN_STICKERS Sticker nötig (aktuell ${stickers.size})."
        }
        if (stickers.size > MAX_STICKERS) {
            out += "Höchstens $MAX_STICKERS Sticker pro Pack (aktuell ${stickers.size})."
        }
        val animated = stickers.count { it.animated }
        if (animated in 1 until stickers.size) {
            out += "Animierte und normale Sticker dürfen nicht gemischt werden ($animated animiert, ${stickers.size - animated} normal)."
        }
        val wrongSize = stickers.count { it.width != STICKER_PX || it.height != STICKER_PX }
        if (wrongSize > 0) out += "$wrongSize Sticker haben nicht das Format 512 × 512."
        val tooBig = stickers.count {
            it.sizeBytes > if (it.animated) MAX_ANIMATED_BYTES else MAX_STATIC_BYTES
        }
        if (tooBig > 0) out += "$tooBig Sticker sind zu groß für WhatsApp."
        return out
    }

    fun installedPackage(ctx: Context): String? = PACKAGES.firstOrNull { pkg ->
        try {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun addIntent(pack: Pack, whatsAppPackage: String): Intent =
        Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", AUTHORITY)
            putExtra("sticker_pack_name", pack.name)
            setPackage(whatsAppPackage)
        }

    /** Erzeugt das kleine 96×96-Vorschaubild des Packs aus dem ersten Sticker. */
    fun ensureTray(ctx: Context, pack: Pack, force: Boolean = false): File {
        val dir = File(ctx.filesDir, "packs/${pack.id}").apply { mkdirs() }
        val tray = File(dir, "tray.png")
        if (tray.exists() && !force) return tray
        val firstId = pack.stickerIds.firstOrNull() ?: return tray
        val src = Library.get(ctx).stickerFile(firstId)
        if (!src.exists()) return tray
        try {
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(src)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(TRAY_PX, TRAY_PX)
            }
            val out = Bitmap.createBitmap(TRAY_PX, TRAY_PX, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(decoded, null, Rect(0, 0, TRAY_PX, TRAY_PX), Paint(Paint.FILTER_BITMAP_FLAG))
            tray.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
        return tray
    }
}
