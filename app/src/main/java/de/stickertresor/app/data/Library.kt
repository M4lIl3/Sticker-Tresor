package de.stickertresor.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Die Sticker-Bibliothek: eigene Kopie jedes Stickers im App-Speicher plus
 * Kategorien und Packs, gespeichert als JSON. Änderungen laufen über [update].
 */
class Library private constructor(private val ctx: Context) {

    private val dataFile = File(ctx.filesDir, "library.json")
    val stickerDir: File = File(ctx.filesDir, "stickers").apply { mkdirs() }
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state

    fun stickerFile(id: String) = File(stickerDir, "$id.webp")

    fun update(transform: (LibraryState) -> LibraryState) {
        synchronized(lock) {
            val next = transform(_state.value)
            _state.value = next
            save(next)
        }
    }

    // ---------- Sticker ----------

    fun addStickers(list: List<Sticker>) {
        if (list.isEmpty()) return
        update { s ->
            val existing = s.stickers.map { it.id }.toSet()
            val fresh = list.filter { it.id !in existing }
            s.copy(stickers = (fresh + s.stickers).sortedByDescending { it.addedAt })
        }
    }

    fun deleteStickers(ids: Set<String>) {
        val removed = state.value.stickers.filter { it.id in ids }
        update { s ->
            s.copy(
                stickers = s.stickers.filter { it.id !in ids },
                packs = s.packs.map { p ->
                    if (p.stickerIds.any { it in ids }) {
                        p.copy(stickerIds = p.stickerIds.filter { it !in ids }, version = p.version + 1)
                    } else p
                },
                ignored = s.ignored + ids,
            )
        }
        removed.forEach { st ->
            stickerFile(st.id).delete()
            st.galleryUri?.let { uri ->
                try {
                    ctx.contentResolver.delete(Uri.parse(uri), null, null)
                } catch (_: Exception) {
                    // Kopie in der Galerie gehört uns nicht mehr (z. B. nach Neuinstallation) – egal.
                }
            }
        }
    }

    fun setEmojis(id: String, emojis: List<String>) = update { s ->
        val touchedPacks = s.packs.filter { id in it.stickerIds }.map { it.id }.toSet()
        s.copy(
            stickers = s.stickers.map { if (it.id == id) it.copy(emojis = emojis) else it },
            packs = s.packs.map { if (it.id in touchedPacks) it.copy(version = it.version + 1) else it },
        )
    }

    // ---------- Kategorien ----------

    fun addCategory(name: String, color: Int): Category {
        val cat = Category(newId(), name.trim(), color)
        update { s -> s.copy(categories = s.categories + cat) }
        return cat
    }

    fun editCategory(id: String, name: String, color: Int) = update { s ->
        s.copy(categories = s.categories.map { if (it.id == id) it.copy(name = name.trim(), color = color) else it })
    }

    fun deleteCategory(id: String) = update { s ->
        s.copy(
            categories = s.categories.filter { it.id != id },
            stickers = s.stickers.map { if (id in it.categories) it.copy(categories = it.categories - id) else it },
        )
    }

    fun setCategory(stickerIds: Set<String>, categoryId: String, assigned: Boolean) = update { s ->
        s.copy(stickers = s.stickers.map {
            if (it.id in stickerIds) {
                it.copy(categories = if (assigned) it.categories + categoryId else it.categories - categoryId)
            } else it
        })
    }

    // ---------- Packs ----------

    fun createPack(name: String, stickerIds: List<String> = emptyList()): Pack {
        val pack = Pack(newId(), name.trim(), stickerIds.distinct(), 1, System.currentTimeMillis())
        update { s -> s.copy(packs = listOf(pack) + s.packs) }
        return pack
    }

    fun renamePack(id: String, name: String) = update { s ->
        s.copy(packs = s.packs.map { if (it.id == id) it.copy(name = name.trim(), version = it.version + 1) else it })
    }

    fun deletePack(id: String) {
        update { s -> s.copy(packs = s.packs.filter { it.id != id }) }
        File(ctx.filesDir, "packs/$id").deleteRecursively()
    }

    fun addToPack(packId: String, stickerIds: List<String>) = update { s ->
        s.copy(packs = s.packs.map { p ->
            if (p.id == packId) {
                val merged = (p.stickerIds + stickerIds).distinct()
                if (merged == p.stickerIds) p else p.copy(stickerIds = merged, version = p.version + 1)
            } else p
        })
    }

    fun removeFromPack(packId: String, stickerId: String) = update { s ->
        s.copy(packs = s.packs.map { p ->
            if (p.id == packId) p.copy(stickerIds = p.stickerIds - stickerId, version = p.version + 1) else p
        })
    }

    // ---------- Speichern / Laden ----------

    private fun save(s: LibraryState) {
        val root = JSONObject()
        root.put("v", 1)
        root.put("stickers", JSONArray().apply {
            s.stickers.forEach { st ->
                put(JSONObject().apply {
                    put("id", st.id)
                    put("a", st.animated)
                    put("t", st.addedAt)
                    put("s", st.sizeBytes)
                    put("w", st.width)
                    put("h", st.height)
                    put("c", JSONArray(st.categories.toList()))
                    put("e", JSONArray(st.emojis))
                    st.galleryUri?.let { put("g", it) }
                })
            }
        })
        root.put("categories", JSONArray().apply {
            s.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("color", c.color)
                })
            }
        })
        root.put("packs", JSONArray().apply {
            s.packs.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("ids", JSONArray(p.stickerIds))
                    put("version", p.version)
                    put("created", p.createdAt)
                })
            }
        })
        root.put("ignored", JSONArray(s.ignored.toList()))

        val tmp = File(dataFile.parentFile, dataFile.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(root.toString())
            tmp.delete()
        }
    }

    private fun load(): LibraryState {
        if (!dataFile.exists()) return LibraryState()
        return try {
            val root = JSONObject(dataFile.readText())
            val stickers = root.optJSONArray("stickers").objects().map { o ->
                Sticker(
                    id = o.getString("id"),
                    animated = o.optBoolean("a"),
                    addedAt = o.optLong("t"),
                    sizeBytes = o.optLong("s"),
                    width = o.optInt("w"),
                    height = o.optInt("h"),
                    categories = o.optJSONArray("c").strings().toSet(),
                    emojis = o.optJSONArray("e").strings(),
                    galleryUri = if (o.has("g")) o.getString("g") else null,
                )
            }
            val categories = root.optJSONArray("categories").objects().map { o ->
                Category(o.getString("id"), o.getString("name"), o.optInt("color"))
            }
            val packs = root.optJSONArray("packs").objects().map { o ->
                Pack(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    stickerIds = o.optJSONArray("ids").strings(),
                    version = o.optInt("version", 1),
                    createdAt = o.optLong("created"),
                )
            }
            LibraryState(
                stickers = stickers.sortedByDescending { it.addedAt },
                categories = categories,
                packs = packs,
                ignored = root.optJSONArray("ignored").strings().toSet(),
            )
        } catch (e: Exception) {
            LibraryState()
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    companion object {
        @Volatile
        private var instance: Library? = null

        fun get(ctx: Context): Library =
            instance ?: synchronized(this) {
                instance ?: Library(ctx.applicationContext).also { instance = it }
            }

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
