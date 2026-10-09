package de.stickertresor.app.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.stickertresor.app.R
import de.stickertresor.app.data.Backup
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TresorTheme { App() }
        }
    }
}

enum class Tab(val label: String) { Tresor("Tresor"), Kategorien("Kategorien"), Packs("Packs"), Einstellungen("Mehr") }

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(Tab.Tresor) }
    var tresorFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var openPackId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerForPack by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }
    val connectFolder = { pickFolder.launch(Backup.WHATSAPP_STICKER_DIR) }

    val whatsApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            val error = result.data?.getStringExtra("validation_error")
            if (error != null) vm.message("WhatsApp meldet: $error")
        } else if (result.resultCode == Activity.RESULT_OK) {
            vm.message("Pack ist jetzt in WhatsApp 🎉")
        }
    }

    BackHandler(enabled = pickerForPack != null || openPackId != null || tab != Tab.Tresor) {
        when {
            pickerForPack != null -> pickerForPack = null
            openPackId != null -> openPackId = null
            else -> tab = Tab.Tresor
        }
    }

    Scaffold(
        containerColor = Palette.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (pickerForPack == null && openPackId == null) {
                NavigationBar(containerColor = Palette.Surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                when (t) {
                                    Tab.Tresor -> Icon(painterResource(R.drawable.ic_grid), null)
                                    Tab.Kategorien -> Icon(painterResource(R.drawable.ic_label), null)
                                    Tab.Packs -> Icon(painterResource(R.drawable.ic_collections), null)
                                    Tab.Einstellungen -> Icon(Icons.Default.Settings, null)
                                }
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.AccentDark,
                                indicatorColor = Palette.Accent,
                                selectedTextColor = Palette.Text,
                                unselectedIconColor = Palette.TextMuted,
                                unselectedTextColor = Palette.TextMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        val pickerPack = pickerForPack?.let { id -> state.packs.firstOrNull { it.id == id } }
        val openPack = openPackId?.let { id -> state.packs.firstOrNull { it.id == id } }
        when {
            pickerPack != null -> PackPickerScreen(
                state = state,
                pack = pickerPack,
                modifier = modifier,
                onDone = { ids ->
                    if (ids.isNotEmpty()) vm.addToPack(pickerPack.id, ids)
                    pickerForPack = null
                },
                onCancel = { pickerForPack = null },
            )

            openPack != null -> PackDetailScreen(
                state = state,
                pack = openPack,
                modifier = modifier,
                onBack = { openPackId = null },
                onAddStickers = { pickerForPack = openPack.id },
                onRemove = { vm.removeFromPack(openPack.id, it) },
                onRename = { vm.renamePack(openPack.id, it) },
                onDelete = {
                    vm.deletePack(openPack.id)
                    openPackId = null
                },
                onAddToWhatsApp = {
                    scope.launch {
                        vm.prepareWhatsApp(openPack)?.let { intent ->
                            try {
                                whatsApp.launch(intent)
                            } catch (e: Exception) {
                                vm.message("WhatsApp konnte nicht geöffnet werden.")
                            }
                        }
                    }
                },
            )

            else -> when (tab) {
                Tab.Tresor -> TresorScreen(
                    state = state,
                    backup = backup,
                    vm = vm,
                    filter = tresorFilter,
                    onFilter = { tresorFilter = it },
                    onConnect = connectFolder,
                    onOpenPack = { openPackId = it },
                    modifier = modifier,
                )

                Tab.Kategorien -> CategoriesScreen(
                    state = state,
                    vm = vm,
                    modifier = modifier,
                    onOpen = { id ->
                        tresorFilter = "cat:$id"
                        tab = Tab.Tresor
                    },
                    onPackCreated = { openPackId = it },
                )

                Tab.Packs -> PacksScreen(
                    state = state,
                    vm = vm,
                    modifier = modifier,
                    onOpen = { openPackId = it },
                )

                Tab.Einstellungen -> SettingsScreen(
                    backup = backup,
                    stickerCount = state.stickers.size,
                    modifier = modifier,
                    onConnect = connectFolder,
                    onBackup = vm::runBackup,
                    onAuto = vm::setAuto,
                )
            }
        }
    }
}
