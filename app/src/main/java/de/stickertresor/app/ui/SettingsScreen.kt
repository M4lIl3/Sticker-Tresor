package de.stickertresor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.stickertresor.app.R

@Composable
fun SettingsScreen(
    backup: BackupUi,
    stickerCount: Int,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit,
    onBackup: () -> Unit,
    onAuto: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp, bottom = 24.dp)
    ) {
        Text("Mehr", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
        Spacer(Modifier.height(16.dp))

        SettingsCard("WhatsApp-Ordner") {
            Text(
                if (backup.connected) "Verbunden ✓ · " + lastBackupText(backup.lastBackup)
                else "Nicht verbunden. Die App braucht einmalig Zugriff auf den Sticker-Ordner von WhatsApp.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                FilledTonalButton(onClick = onConnect, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (backup.connected) "Neu verbinden" else "Verbinden")
                }
                if (backup.connected) {
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = onBackup, enabled = !backup.running, modifier = Modifier.weight(1f)) {
                        Text(if (backup.running) "Sichere …" else "Jetzt sichern")
                    }
                }
            }
        }

        SettingsCard("Automatisch sichern") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Einmal täglich, während das Handy lädt, neue Sticker sichern.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = backup.auto,
                    onCheckedChange = onAuto,
                    enabled = backup.connected,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.AccentDark,
                        checkedTrackColor = Palette.Accent,
                    ),
                )
            }
        }

        SettingsCard("Wo liegen meine Sticker?") {
            Text(
                "$stickerCount Sticker sind im Tresor. Zusätzlich liegt jede Sicherung als Bild unter " +
                    "Bilder › Sticker-Tresor – aktiviere diesen Ordner in Google Fotos (Sammlung › Geräteordner), " +
                    "dann sind sie auch in der Cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        }

        SettingsCard("Packs in WhatsApp") {
            Text(
                "Hinzugefügte Packs findest du in WhatsApp im Sticker-Bereich. Änderungen am Pack übernimmt WhatsApp automatisch. " +
                    "Damit sie dort sichtbar bleiben, muss Sticker-Tresor installiert bleiben.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Sticker-Tresor $version",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Palette.Card)
            .border(1.dp, Palette.Line, MaterialTheme.shapes.large)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
