@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package de.stickertresor.app.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.stickertresor.app.data.Library
import de.stickertresor.app.data.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Zeigt einen Sticker an – animierte Sticker laufen ab. */
@Composable
fun StickerImage(id: String, modifier: Modifier = Modifier, sizePx: Int = 256) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val drawable by produceState<Drawable?>(null, id, sizePx) {
        value = withContext(Dispatchers.IO) { decodeSticker(Library.get(ctx).stickerFile(id), sizePx) }
    }
    AndroidView(
        factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            val d = drawable
            if (view.drawable !== d) {
                view.setImageDrawable(d)
                (d as? AnimatedImageDrawable)?.start()
            }
        },
        modifier = modifier,
    )
}

private fun decodeSticker(file: File, sizePx: Int): Drawable? = try {
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        if (sizePx in 1 until w && h > 0) {
            decoder.setTargetSize(sizePx, (sizePx.toLong() * h / w).toInt().coerceAtLeast(1))
        }
    }
} catch (e: Exception) {
    null
}

/** Raster aus Stickern, mit Auswahl-Markierung. */
@Composable
fun StickerGrid(
    stickers: List<Sticker>,
    selected: Set<String>,
    onClick: (Sticker) -> Unit,
    onLongClick: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    header: (@Composable () -> Unit)? = null,
    badge: (@Composable (Sticker) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(stickers, key = { it.id }) { st ->
            val isSelected = st.id in selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isSelected) Palette.AccentSoft else Palette.Card)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Palette.Accent else Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .combinedClickable(onClick = { onClick(st) }, onLongClick = { onLongClick(st) })
            ) {
                StickerImage(st.id, Modifier.fillMaxSize().padding(8.dp))
                if (isSelected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Palette.Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Check, null, tint = Palette.AccentDark, modifier = Modifier.size(16.dp))
                    }
                }
                badge?.invoke(st)
            }
        }
    }
}

/** Kleiner farbiger Punkt für Kategorien. */
@Composable
fun ColorDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Pillenförmiger Filter-Chip im eigenen Look. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    dot: Color? = null,
    count: Int? = null,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.Card)
            .border(1.dp, if (selected) Palette.Accent else Palette.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            ColorDot(if (selected) Palette.AccentDark else dot, 8.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Palette.AccentDark else Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Palette.AccentDark.copy(alpha = 0.7f) else Palette.TextMuted,
            )
        }
    }
}

/** Dialog zum Anlegen/Bearbeiten einer Kategorie. */
@Composable
fun CategoryDialog(
    title: String,
    initialName: String = "",
    initialColor: Int = 0,
    confirmText: String = "Speichern",
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.CardHigh,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Spacer(Modifier.size(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.CategoryColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(3.dp, if (i == color) Palette.Text else Color.Transparent, CircleShape)
                                .clickable { color = i },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), color) }, enabled = name.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Einfacher Texteingabe-Dialog (z. B. Pack-Name). */
@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirmText: String = "Speichern",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.CardHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.CardHigh,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = Palette.Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Wandelt eine Eingabe in höchstens drei Emojis um. */
fun parseEmojis(input: String): List<String> {
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(input)
    val out = mutableListOf<String>()
    var start = it.first()
    var end = it.next()
    while (end != android.icu.text.BreakIterator.DONE) {
        val g = input.substring(start, end)
        val cp = g.codePointAt(0)
        val isEmoji = cp >= 0x2190 && !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp)
        if (isEmoji && g !in out) out += g
        start = end
        end = it.next()
    }
    return out.take(3)
}
