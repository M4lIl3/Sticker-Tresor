package de.stickertresor.app

import android.content.Intent
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var statusText: TextView
    private lateinit var btnFolder: MaterialButton
    private lateinit var btnBackup: MaterialButton
    private lateinit var switchAuto: MaterialSwitch
    private lateinit var progress: LinearProgressIndicator
    private lateinit var galleryTitle: TextView
    private lateinit var emptyText: TextView
    private lateinit var adapter: StickerAdapter

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onFolderPicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        statusText = findViewById(R.id.status_text)
        btnFolder = findViewById(R.id.btn_folder)
        btnBackup = findViewById(R.id.btn_backup)
        switchAuto = findViewById(R.id.switch_auto)
        progress = findViewById(R.id.progress)
        galleryTitle = findViewById(R.id.gallery_title)
        emptyText = findViewById(R.id.empty_text)

        val basePadding = root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                basePadding + bars.left,
                basePadding + bars.top,
                basePadding + bars.right,
                bars.bottom
            )
            insets
        }

        val columns = 4
        adapter = StickerAdapter(lifecycleScope, columns) { showSticker(it) }
        findViewById<RecyclerView>(R.id.gallery).apply {
            layoutManager = GridLayoutManager(this@MainActivity, columns)
            adapter = this@MainActivity.adapter
        }

        btnFolder.setOnClickListener { pickFolder.launch(StickerStore.WHATSAPP_STICKER_DIR) }
        btnBackup.setOnClickListener { runBackup() }

        switchAuto.isChecked = StickerStore.isAutoBackup(this)
        switchAuto.setOnCheckedChangeListener { _, checked ->
            StickerStore.setAutoBackup(this, checked)
            if (checked) BackupWorker.schedule(this) else BackupWorker.cancel(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onFolderPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            message(getString(R.string.folder_error))
            return
        }
        StickerStore.saveTreeUri(this, uri)
        if (!Uri.decode(uri.toString()).contains("Sticker", ignoreCase = true)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.wrong_folder_title)
                .setMessage(R.string.wrong_folder_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        refresh()
    }

    private fun refresh() {
        val connected = StickerStore.treeUri(this) != null
        btnBackup.isEnabled = connected
        switchAuto.isEnabled = connected
        btnFolder.text = getString(if (connected) R.string.btn_folder_change else R.string.btn_folder)

        lifecycleScope.launch {
            val backups = withContext(Dispatchers.IO) { StickerStore.listBackups(this@MainActivity) }
            adapter.submit(backups)
            galleryTitle.text = getString(R.string.gallery_title, backups.size)
            emptyText.visibility = if (backups.isEmpty()) View.VISIBLE else View.GONE
            emptyText.text = getString(if (connected) R.string.empty_connected else R.string.empty_not_connected)

            val last = StickerStore.lastBackup(this@MainActivity)
            statusText.text = when {
                !connected -> getString(R.string.status_not_connected)
                last == 0L -> getString(R.string.status_connected_never)
                else -> getString(
                    R.string.status_connected_last,
                    DateFormat.getMediumDateFormat(this@MainActivity).format(Date(last)) + ", " +
                        DateFormat.getTimeFormat(this@MainActivity).format(Date(last))
                )
            }
        }
    }

    private fun runBackup() {
        btnBackup.isEnabled = false
        btnFolder.isEnabled = false
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    StickerStore.backup(this@MainActivity) { done, total ->
                        runOnUiThread {
                            if (total > 0) {
                                progress.isIndeterminate = false
                                progress.max = total
                                progress.setProgressCompat(done, false)
                            }
                        }
                    }
                }
                var text = when {
                    result.found == 0 -> getString(R.string.result_none_found)
                    result.saved == 0 -> getString(R.string.result_nothing_new, result.found)
                    else -> getString(R.string.result_saved, result.saved, result.alreadySaved)
                }
                if (result.failed > 0) {
                    text += " " + getString(R.string.result_failed, result.failed)
                }
                message(text)
            } catch (e: Exception) {
                message(getString(R.string.backup_error, e.message ?: e.javaClass.simpleName))
            } finally {
                progress.visibility = View.GONE
                btnFolder.isEnabled = true
                refresh()
            }
        }
    }

    private fun showSticker(uri: Uri) {
        val image = ImageView(this).apply {
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            adjustViewBounds = true
        }
        lifecycleScope.launch {
            val drawable = withContext(Dispatchers.IO) { StickerStore.decode(this@MainActivity, uri, 0) }
            image.setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.start()
        }
        MaterialAlertDialogBuilder(this)
            .setView(image)
            .setPositiveButton(R.string.share) { _, _ -> share(uri) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    private fun message(text: String) {
        Snackbar.make(root, text, Snackbar.LENGTH_LONG).show()
    }
}
