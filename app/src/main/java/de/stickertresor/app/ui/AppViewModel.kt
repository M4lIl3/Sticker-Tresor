package de.stickertresor.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.stickertresor.app.BackupWorker
import de.stickertresor.app.data.Backup
import de.stickertresor.app.data.Library
import de.stickertresor.app.data.LibraryState
import de.stickertresor.app.data.Pack
import de.stickertresor.app.whatsapp.WhatsAppPacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUi(
    val connected: Boolean = false,
    val running: Boolean = false,
    val progress: Float? = null,
    val lastBackup: Long = 0L,
    val auto: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val library = Library.get(app)

    val state: StateFlow<LibraryState> = library.state

    private val _backup = MutableStateFlow(BackupUi())
    val backup: StateFlow<BackupUi> = _backup

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    init {
        refreshBackupInfo()
        viewModelScope.launch(Dispatchers.IO) { Backup.migrateFromV1(ctx) }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                say("Fehler: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // ---------- Sicherung ----------

    fun refreshBackupInfo() {
        _backup.value = _backup.value.copy(
            connected = Backup.treeUri(ctx) != null,
            lastBackup = Backup.lastBackup(ctx),
            auto = Backup.isAutoBackup(ctx),
        )
    }

    fun onFolderPicked(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            say("Der Ordner konnte nicht dauerhaft freigegeben werden.")
            return
        }
        Backup.saveTreeUri(ctx, uri)
        refreshBackupInfo()
        if (!Uri.decode(uri.toString()).contains("Sticker", ignoreCase = true)) {
            say("Hm, das scheint nicht der WhatsApp-Sticker-Ordner zu sein.")
        } else {
            say("WhatsApp-Ordner verbunden ✓")
            runBackup()
        }
    }

    fun runBackup() {
        if (_backup.value.running) return
        _backup.value = _backup.value.copy(running = true, progress = null)
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    Backup.run(ctx) { done, total ->
                        if (total > 0) _backup.value = _backup.value.copy(progress = done.toFloat() / total)
                    }
                }
                say(
                    when {
                        r.found == 0 -> "Im WhatsApp-Ordner wurden keine Sticker gefunden."
                        r.saved == 0 -> "Alles schon gesichert – nichts Neues."
                        else -> "${r.saved} neue Sticker gesichert 🎉"
                    } + if (r.failed > 0) " (${r.failed} nicht lesbar)" else ""
                )
            } catch (e: Exception) {
                say("Sicherung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _backup.value = _backup.value.copy(running = false, progress = null)
                refreshBackupInfo()
            }
        }
    }

    fun setAuto(enabled: Boolean) {
        Backup.setAutoBackup(ctx, enabled)
        if (enabled) BackupWorker.schedule(ctx) else BackupWorker.cancel(ctx)
        refreshBackupInfo()
    }

    // ---------- Sticker ----------

    fun deleteStickers(ids: Set<String>) = io {
        library.deleteStickers(ids)
        say(if (ids.size == 1) "Sticker gelöscht" else "${ids.size} Sticker gelöscht")
    }

    fun setEmojis(id: String, emojis: List<String>) = io { library.setEmojis(id, emojis) }

    fun shareIntent(id: String): Intent {
        val file = library.stickerFile(id)
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "de.stickertresor.app.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Sticker teilen")
    }

    // ---------- Kategorien ----------

    fun addCategory(name: String, color: Int, assignTo: Set<String> = emptySet()) = io {
        val cat = library.addCategory(name, color)
        if (assignTo.isNotEmpty()) library.setCategory(assignTo, cat.id, true)
    }

    fun editCategory(id: String, name: String, color: Int) = io { library.editCategory(id, name, color) }

    fun deleteCategory(id: String) = io { library.deleteCategory(id) }

    fun setCategory(ids: Set<String>, categoryId: String, assigned: Boolean) =
        io { library.setCategory(ids, categoryId, assigned) }

    // ---------- Packs ----------

    fun createPack(name: String, stickerIds: List<String>, onCreated: (Pack) -> Unit = {}) {
        viewModelScope.launch {
            val pack = withContext(Dispatchers.IO) { library.createPack(name, stickerIds) }
            onCreated(pack)
            if (stickerIds.size > WhatsAppPacks.MAX_STICKERS) {
                say("Achtung: WhatsApp erlaubt höchstens ${WhatsAppPacks.MAX_STICKERS} Sticker pro Pack.")
            }
        }
    }

    fun renamePack(id: String, name: String) = io { library.renamePack(id, name) }

    fun deletePack(id: String) = io { library.deletePack(id) }

    fun addToPack(packId: String, ids: List<String>) = io {
        library.addToPack(packId, ids)
        say(if (ids.size == 1) "Zum Pack hinzugefügt" else "${ids.size} Sticker zum Pack hinzugefügt")
    }

    fun removeFromPack(packId: String, stickerId: String) = io { library.removeFromPack(packId, stickerId) }

    /** Bereitet das Pack für WhatsApp vor. Liefert den Intent oder null mit Meldung. */
    suspend fun prepareWhatsApp(pack: Pack): Intent? {
        val pkg = WhatsAppPacks.installedPackage(ctx)
        if (pkg == null) {
            say("WhatsApp ist auf diesem Handy nicht installiert.")
            return null
        }
        val problems = WhatsAppPacks.problems(state.value, pack)
        if (problems.isNotEmpty()) {
            say(problems.first())
            return null
        }
        withContext(Dispatchers.IO) { WhatsAppPacks.ensureTray(ctx, pack, force = true) }
        return WhatsAppPacks.addIntent(pack, pkg)
    }

    fun message(text: String) = say(text)
}
