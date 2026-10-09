package de.stickertresor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.stickertresor.app.data.LibraryState
import de.stickertresor.app.data.Pack
import de.stickertresor.app.whatsapp.WhatsAppPacks

// ---------- Übersicht ----------

@Composable
fun PacksScreen(
    state: LibraryState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Packs", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
                Text(
                    "Stell eigene Sticker-Packs zusammen und füge sie mit einem Tipp zu WhatsApp hinzu. " +
                        "WhatsApp braucht ${WhatsAppPacks.MIN_STICKERS}–${WhatsAppPacks.MAX_STICKERS} Sticker pro Pack, entweder alle animiert oder alle statisch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            if (state.packs.isEmpty()) {
                item { EmptyHint("Noch keine Packs. Erstelle eins hier oder wähle im Tresor Sticker aus und tippe auf „Zu Pack“.") }
            }
            items(state.packs, key = { it.id }) { p ->
                PackCard(state, p) { onOpen(p.id) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            containerColor = Palette.Accent,
            contentColor = Palette.AccentDark,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Neues Pack") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        NameDialog(
            title = "Neues Pack",
            label = "Name des Packs",
            confirmText = "Erstellen",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                vm.createPack(name, emptyList()) { onOpen(it.id) }
            },
        )
    }
}

@Composable
private fun PackCard(state: LibraryState, pack: Pack, onClick: () -> Unit) {
    val problems = WhatsAppPacks.problems(state, pack)
    val animated = WhatsAppPacks.isAnimated(state, pack)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(Palette.CardHigh),
            contentAlignment = Alignment.Center,
        ) {
            val first = pack.stickerIds.firstOrNull()
            if (first != null) {
                StickerImage(first, Modifier.fillMaxSize().padding(6.dp), sizePx = 160)
            } else {
                Icon(Icons.Default.Add, null, tint = Palette.TextMuted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pack.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${pack.stickerIds.size} Sticker" + if (pack.stickerIds.isNotEmpty()) (if (animated) " · animiert" else " · statisch") else "",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            StatusChip(ready = problems.isEmpty())
        }
    }
}

@Composable
private fun StatusChip(ready: Boolean) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (ready) Palette.AccentSoft else Palette.CardHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ready) Icons.Default.Check else Icons.Default.Warning,
            null,
            tint = if (ready) Palette.Accent else Palette.Warm,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ready) "Bereit für WhatsApp" else "Noch nicht bereit",
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) Palette.Accent else Palette.Warm,
        )
    }
}

// ---------- Pack bearbeiten ----------

@Composable
fun PackDetailScreen(
    state: LibraryState,
    pack: Pack,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAddStickers: () -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddToWhatsApp: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val problems = WhatsAppPacks.problems(state, pack)
    val stickers = pack.stickerIds.mapNotNull { state.stickerById[it] }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Palette.Text) }
            Text(
                pack.name,
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mehr", tint = Palette.Text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Pack löschen", color = Palette.Danger) }, onClick = { menu = false; deleting = true })
                }
            }
        }

        StickerGrid(
            stickers = stickers,
            selected = emptySet(),
            onClick = {},
            onLongClick = {},
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            header = {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(if (problems.isEmpty()) Palette.AccentSoft else Palette.Card)
                            .border(1.dp, if (problems.isEmpty()) Palette.Accent.copy(alpha = 0.4f) else Palette.Line, MaterialTheme.shapes.large)
                            .padding(16.dp)
                    ) {
                        if (problems.isEmpty()) {
                            Text("Bereit für WhatsApp ✓", style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
                            Text(
                                "${stickers.size} Sticker · " + (if (WhatsAppPacks.isAnimated(state, pack)) "animiert" else "statisch"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Text,
                            )
                        } else {
                            Text("Noch nicht bereit", style = MaterialTheme.typography.titleMedium, color = Palette.Warm)
                            problems.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (stickers.isNotEmpty()) {
                        Text(
                            "Tippe auf das ✕, um einen Sticker aus dem Pack zu nehmen. Der erste Sticker wird das Pack-Symbol.",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextMuted,
                        )
                    } else {
                        EmptyHint("Das Pack ist noch leer.")
                    }
                }
            },
            badge = { st ->
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Palette.Background.copy(alpha = 0.85f))
                            .clickable { onRemove(st.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, "Entfernen", tint = Palette.Text, modifier = Modifier.size(16.dp))
                    }
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(onClick = onAddStickers, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sticker")
            }
            Button(onClick = onAddToWhatsApp, enabled = problems.isEmpty(), modifier = Modifier.weight(1.6f)) {
                Text("Zu WhatsApp hinzufügen")
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Pack umbenennen",
            initial = pack.name,
            onDismiss = { renaming = false },
            onConfirm = {
                onRename(it)
                renaming = false
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Pack „${pack.name}“ löschen?",
            text = "Die Sticker bleiben im Tresor. Ist das Pack schon in WhatsApp, verschwindet es dort beim nächsten Abgleich.",
            confirmText = "Löschen",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                onDelete()
            },
        )
    }
}

// ---------- Sticker für ein Pack auswählen ----------

@Composable
fun PackPickerScreen(
    state: LibraryState,
    pack: Pack,
    modifier: Modifier = Modifier,
    onDone: (List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var selection by remember { mutableStateOf(listOf<String>()) }
    var filter by remember { mutableStateOf<String?>(null) }
    val available = applyFilter(state, filter).filter { it.id !in pack.stickerIds }
    val total = pack.stickerIds.size + selection.size

    BackHandler { onCancel() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Abbrechen", tint = Palette.Text) }
            Column(Modifier.weight(1f)) {
                Text("Sticker hinzufügen", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text(
                    "zu „${pack.name}“ · danach $total von max. ${WhatsAppPacks.MAX_STICKERS}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (total > WhatsAppPacks.MAX_STICKERS) Palette.Warm else Palette.TextMuted,
                )
            }
        }
        StickerGrid(
            stickers = available,
            selected = selection.toSet(),
            onClick = { st -> selection = if (st.id in selection) selection - st.id else selection + st.id },
            onLongClick = { st -> selection = if (st.id in selection) selection - st.id else selection + st.id },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            header = {
                Column {
                    FilterRow(state, filter) { filter = it }
                    if (available.isEmpty()) EmptyHint("Keine weiteren Sticker in dieser Ansicht.")
                }
            },
        )
        Row(Modifier.fillMaxWidth().background(Palette.Surface).padding(16.dp)) {
            Button(onClick = { onDone(selection) }, enabled = selection.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (selection.isEmpty()) "Sticker antippen zum Auswählen" else "${selection.size} hinzufügen")
            }
        }
    }
}
