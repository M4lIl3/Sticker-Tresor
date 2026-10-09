package de.stickertresor.app.data

/** Ein gesicherter Sticker. [id] ist aus dem Inhalt (SHA-256) abgeleitet – gleiche Datei, gleiche ID. */
data class Sticker(
    val id: String,
    val animated: Boolean,
    val addedAt: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val categories: Set<String> = emptySet(),
    val emojis: List<String> = emptyList(),
    val galleryUri: String? = null,
)

data class Category(
    val id: String,
    val name: String,
    val color: Int,
)

data class Pack(
    val id: String,
    val name: String,
    val stickerIds: List<String>,
    /** Wird bei jeder Änderung hochgezählt, damit WhatsApp die neuen Bilder lädt. */
    val version: Int,
    val createdAt: Long,
)

data class LibraryState(
    val stickers: List<Sticker> = emptyList(),
    val categories: List<Category> = emptyList(),
    val packs: List<Pack> = emptyList(),
    /** Sticker, die bewusst gelöscht wurden und nicht erneut gesichert werden sollen. */
    val ignored: Set<String> = emptySet(),
) {
    val stickerById: Map<String, Sticker> by lazy { stickers.associateBy { it.id } }
}
