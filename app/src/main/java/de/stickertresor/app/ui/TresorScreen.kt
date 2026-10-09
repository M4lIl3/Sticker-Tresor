@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.stickertresor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.stickertresor.app.R
import de.stickertresor.app.data.LibraryState
import de.stickertresor.app.data.Sticker
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

fun applyFilter(state: LibraryState, filter: String?): List<Sticker> {
    val now = System.currentTimeMillis()
    val f = filter ?: return state.stickers
    return when {
        f == "new" -> state.stickers.filter { now - it.addedAt < WEEK_MS }
        f == "anim" -> state.stickers.filter { it.animated }
        f == "static" -> state.stickers.filter { !it.animated }
        f == "none" -> state.stickers.filter { it.categories.isEmpty() }
        f.startsWith("cat:") -> f.removePrefix("cat:").let { id -> state.stickers.filter { id in it.categories } }
        else -> state.stickers
    }
}

@Composable
fun FilterRow(state: LibraryState, filter: String?, onFilter: (String?) -> Unit) {
    val now = System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("Alle", filter == null, { onFilter(null) }, count = state.stickers.size)
        Pill("Neu", filter == "new", { onFilter("new") }, count = state.stickers.count { now - it.addedAt < WEEK_MS })
        Pill("Animiert", filter == "anim", { onFilter("anim") }, count = state.stickers.count { it.animated })
        Pill("Statisch", filter == "static", { onFilter("static") }, count = state.stickers.count { !it.animated })
        state.categories.forEach { c ->
            Pill(
                c.name,
                filter == "cat:${c.id}",
                { onFilter("cat:${c.id}") },
                dot = Palette.category(c.color),
                count = state.stickers.count { c.id in it.categories },
            )
        }
        Pill("Ohne Kategorie", filter == "none", { onFilter("none") }, count = state.stickers.count { it.categories.isEmpty() })
    }
}

@Composable
fun TresorScreen(
    state: LibraryState,
    backup: BackupUi,
    vm: AppViewModel,
    filter: String?,
    onFilter: (String?) -> Unit,
    onConnect: () -> Unit,
    onOpenPack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var selection by remember { mutableStateOf(setOf<String>()) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showPackSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Kategorie-Filter, deren Kategorie gelöscht wurde, zurücksetzen
    val safeFilter = filter?.takeIf { f -> !f.startsWith("cat:") || state.categories.any { "cat:${it.id}" == f } }
    val visible = applyFilter(state, safeFilter)
    val selecting = selection.isNotEmpty()

    BackHandler(enabled = selecting) { selection = emptySet() }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selection.size,
                onClose = { selection = emptySet() },
                onSelectAll = { selection = visible.map { it.id }.toSet() },
                onCategory = { showCategorySheet = true },
                onPack = { showPackSheet = true },
                onDelete = { confirmDelete = true },
            )
        }

        StickerGrid(
            stickers = visible,
            selected = selection,
            onClick = { st ->
                if (selecting) selection = selection.toggle(st.id) else detailId = st.id
            },
            onLongClick = { st -> selection = selection.toggle(st.id) },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
            header = {
                Column {
                    if (!selecting) {
                        Header(state, backup, onConnect, vm::runBackup)
                        Spacer(Modifier.height(16.dp))
                    }
                    FilterRow(state, safeFilter, onFilter)
                    if (visible.isEmpty()) {
                        EmptyHint(
                            when {
                                state.stickers.isEmpty() && !backup.connected -> "Verbinde den WhatsApp-Ordner und sichere deine ersten Sticker."
                                state.stickers.isEmpty() -> "Noch keine Sticker gesichert. Tippe auf „Sichern“."
                                else -> "Keine Sticker in dieser Ansicht."
                            }
                        )
                    } else if (!selecting && state.stickers.size in 1..60) {
                        Text(
                            "Tipp: Lange drücken, um mehrere Sticker auszuwählen",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
        )
    }

    // ---------- Details eines Stickers ----------
    val detail = detailId?.let { state.stickerById[it] }
    if (detail != null) {
        StickerSheet(
            sticker = detail,
            state = state,
            vm = vm,
            onDismiss = { detailId = null },
            onShare = { ctx.startActivity(vm.shareIntent(detail.id)) },
            onAddToPack = {
                selection = setOf(detail.id)
                detailId = null
                showPackSheet = true
            },
            onDelete = {
                vm.deleteStickers(setOf(detail.id))
                detailId = null
            },
        )
    }

    if (showCategorySheet) {
        AssignCategorySheet(
            state = state,
            ids = selection,
            vm = vm,
            onDismiss = { showCategorySheet = false },
        )
    }

    if (showPackSheet) {
        ChoosePackSheet(
            state = state,
            ids = selection,
            vm = vm,
            onDismiss = { showPackSheet = false },
            onDone = { packId ->
                showPackSheet = false
                selection = emptySet()
                if (packId != null) onOpenPack(packId)
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "${selection.size} Sticker löschen?",
            text = "Sie werden aus dem Tresor, aus allen Packs und aus Bilder/Sticker-Tresor entfernt und nicht erneut gesichert.",
            confirmText = "Löschen",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.deleteStickers(selection)
                selection = emptySet()
                confirmDelete = false
            },
        )
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun Header(state: LibraryState, backup: BackupUi, onConnect: () -> Unit, onBackup: () -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Text("Sticker-Tresor", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
        Text(
            "${state.stickers.size} Sticker · ${state.categories.size} Kategorien · ${state.packs.size} Packs",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextMuted,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(Palette.Card)
                .border(1.dp, Palette.Line, MaterialTheme.shapes.large)
                .padding(18.dp)
        ) {
            if (!backup.connected) {
                Text("WhatsApp verbinden", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    "Gib einmalig den Sticker-Ordner frei. Im Auswahlfenster unten auf „Diesen Ordner verwenden“ und „Zulassen“ tippen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ordner verbinden")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (backup.running) "Sichere …" else "Mit WhatsApp verbunden",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Text,
                        )
                        Text(
                            lastBackupText(backup.lastBackup) + if (backup.auto) " · täglich automatisch" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onBackup, enabled = !backup.running) {
                        Icon(painterResource(R.drawable.ic_save), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sichern")
                    }
                }
                if (backup.running) {
                    Spacer(Modifier.height(14.dp))
                    val p = backup.progress
                    if (p == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.Accent, trackColor = Palette.Line)
                    } else {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                            color = Palette.Accent,
                            trackColor = Palette.Line,
                        )
                    }
                }
            }
        }
    }
}

fun lastBackupText(time: Long): String =
    if (time == 0L) "Noch nie gesichert"
    else "Zuletzt: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.GERMANY).format(Date(time))

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCategory: () -> Unit,
    onPack: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Palette.CardHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Auswahl beenden", tint = Palette.Text) }
            Text("$count ausgewählt", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            TextButton(onClick = onSelectAll) { Text("Alle") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Löschen", tint = Palette.Danger) }
        }
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onCategory, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_label), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kategorie")
            }
            FilledTonalButton(onClick = onPack, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_collections), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zu Pack")
            }
        }
    }
}

// ---------- Sheets ----------

@Composable
private fun StickerSheet(
    sticker: Sticker,
    state: LibraryState,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onAddToPack: () -> Unit,
    onDelete: () -> Unit,
) {
    var emojiText by remember(sticker.id) { mutableStateOf(sticker.emojis.joinToString(" ")) }
    var newCategory by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            val parsed = parseEmojis(emojiText)
            if (parsed != sticker.emojis) vm.setEmojis(sticker.id, parsed)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Palette.Card),
                contentAlignment = Alignment.Center,
            ) {
                StickerImage(sticker.id, Modifier.size(210.dp), sizePx = 512)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                (if (sticker.animated) "Animiert" else "Statisch") +
                    " · ${sticker.sizeBytes / 1024} KB · ${sticker.width}×${sticker.height} · gesichert am " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.GERMANY).format(Date(sticker.addedAt)),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextMuted,
            )

            Spacer(Modifier.height(20.dp))
            Text("Kategorien", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c ->
                    val on = c.id in sticker.categories
                    Pill(c.name, on, { vm.setCategory(setOf(sticker.id), c.id, !on) }, dot = Palette.category(c.color))
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Palette.Line, RoundedCornerShape(50))
                        .clickable { newCategory = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Neu", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Emojis für WhatsApp", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Bis zu 3 – damit findet WhatsApp den Sticker über die Emoji-Suche.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = emojiText,
                onValueChange = { emojiText = it.take(40) },
                placeholder = { Text("z. B. 😂 🐶") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Teilen")
                }
                Button(onClick = onAddToPack, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_collections), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zu Pack")
                }
            }
            TextButton(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Palette.Danger),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Löschen")
            }
        }
    }

    if (newCategory) {
        CategoryDialog(
            title = "Neue Kategorie",
            initialColor = state.categories.size,
            confirmText = "Anlegen",
            onDismiss = { newCategory = false },
            onConfirm = { name, color ->
                vm.addCategory(name, color, assignTo = setOf(sticker.id))
                newCategory = false
            },
        )
    }
    if (confirm) {
        ConfirmDialog(
            title = "Sticker löschen?",
            text = "Er wird aus dem Tresor, aus allen Packs und aus Bilder/Sticker-Tresor entfernt.",
            confirmText = "Löschen",
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                onDelete()
            },
        )
    }
}

@Composable
private fun AssignCategorySheet(
    state: LibraryState,
    ids: Set<String>,
    vm: AppViewModel,
    onDismiss: () -> Unit,
) {
    var newCategory by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Kategorie für ${ids.size} Sticker", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Text(
                "Antippen zum Zuordnen, nochmal antippen zum Entfernen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { c ->
                    val selected = ids.mapNotNull { state.stickerById[it] }
                    val all = selected.isNotEmpty() && selected.all { c.id in it.categories }
                    Pill(c.name, all, { vm.setCategory(ids, c.id, !all) }, dot = Palette.category(c.color))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { newCategory = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Neue Kategorie")
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Fertig") }
            }
        }
    }
    if (newCategory) {
        CategoryDialog(
            title = "Neue Kategorie",
            initialColor = state.categories.size,
            confirmText = "Anlegen",
            onDismiss = { newCategory = false },
            onConfirm = { name, color ->
                vm.addCategory(name, color, assignTo = ids)
                newCategory = false
            },
        )
    }
}

@Composable
private fun ChoosePackSheet(
    state: LibraryState,
    ids: Set<String>,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onDone: (String?) -> Unit,
) {
    var newPack by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Zu welchem Pack?", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { newPack = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Neues Pack mit ${ids.size} Sticker${if (ids.size == 1) "" else "n"}")
            }
            Spacer(Modifier.height(8.dp))
            state.packs.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Palette.Card)
                        .clickable {
                            vm.addToPack(p.id, ids.toList())
                            onDone(null)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(Palette.CardHigh)) {
                        p.stickerIds.firstOrNull()?.let { StickerImage(it, Modifier.fillMaxSize().padding(4.dp), sizePx = 128) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text("${p.stickerIds.size} Sticker", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
                    }
                }
            }
        }
    }
    if (newPack) {
        NameDialog(
            title = "Neues Pack",
            label = "Name des Packs",
            confirmText = "Erstellen",
            onDismiss = { newPack = false },
            onConfirm = { name ->
                newPack = false
                vm.createPack(name, ids.toList()) { onDone(it.id) }
            },
        )
    }
}
