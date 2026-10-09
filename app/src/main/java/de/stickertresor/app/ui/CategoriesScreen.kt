package de.stickertresor.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import de.stickertresor.app.data.Category
import de.stickertresor.app.data.LibraryState
import de.stickertresor.app.whatsapp.WhatsAppPacks

@Composable
fun CategoriesScreen(
    state: LibraryState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
    onPackCreated: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Kategorien", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
                Text(
                    "Sortiere deine Sticker nach Themen. Zuordnen geht im Tresor: Sticker antippen oder mehrere lange drücken.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            if (state.categories.isEmpty()) {
                item { EmptyHint("Noch keine Kategorien. Leg deine erste an – z. B. „Lustig“, „Tiere“ oder „Liebe“.") }
            }
            items(state.categories, key = { it.id }) { c ->
                val stickers = state.stickers.filter { c.id in it.categories }
                CategoryCard(
                    category = c,
                    stickerIds = stickers.map { it.id },
                    onClick = { onOpen(c.id) },
                    onEdit = { editing = c },
                    onDelete = { deleting = c },
                    onMakePack = {
                        val ids = stickers.map { it.id }.take(WhatsAppPacks.MAX_STICKERS)
                        vm.createPack(c.name, ids) { onPackCreated(it.id) }
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            containerColor = Palette.Accent,
            contentColor = Palette.AccentDark,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Neue Kategorie") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        CategoryDialog(
            title = "Neue Kategorie",
            initialColor = state.categories.size,
            confirmText = "Anlegen",
            onDismiss = { creating = false },
            onConfirm = { name, color ->
                vm.addCategory(name, color)
                creating = false
            },
        )
    }
    editing?.let { c ->
        CategoryDialog(
            title = "Kategorie bearbeiten",
            initialName = c.name,
            initialColor = c.color,
            onDismiss = { editing = null },
            onConfirm = { name, color ->
                vm.editCategory(c.id, name, color)
                editing = null
            },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "„${c.name}“ löschen?",
            text = "Nur die Kategorie wird gelöscht – die Sticker bleiben im Tresor.",
            confirmText = "Löschen",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteCategory(c.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    stickerIds: List<String>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakePack: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = Palette.category(category.color)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(MaterialTheme.shapes.small).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text(
                    "${stickerIds.size} Sticker",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextMuted,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Mehr", tint = Palette.TextMuted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; onEdit() })
                    if (stickerIds.size >= WhatsAppPacks.MIN_STICKERS) {
                        DropdownMenuItem(text = { Text("Als Pack anlegen") }, onClick = { menu = false; onMakePack() })
                    }
                    DropdownMenuItem(
                        text = { Text("Löschen", color = Palette.Danger) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        if (stickerIds.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stickerIds.take(5).forEach { id ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Palette.CardHigh)
                    ) {
                        StickerImage(id, Modifier.fillMaxSize().padding(4.dp), sizePx = 128)
                    }
                }
                repeat(5 - stickerIds.take(5).size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
