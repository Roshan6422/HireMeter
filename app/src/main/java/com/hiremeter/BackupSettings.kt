package com.hiremeter

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Google Drive backup, and a backup file the driver keeps themselves; restoring from either. */
@Composable
fun BackupSettings() {
    val ctx = LocalContext.current
    val s = Tr.s
    val scope = rememberCoroutineScope()
    val trip by TripRepo.state.collectAsState()
    var drive by remember { mutableStateOf(DriveBackup.state(ctx)) }
    var status by remember { mutableStateOf("") }
    /** A backup read and waiting for the driver to say yes. */
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    /** A Drive file to link once its old backup has been restored (or not). */
    var pendingLink by remember { mutableStateOf<Uri?>(null) }

    fun io(work: () -> Unit, done: (Throwable?) -> Unit) = scope.launch {
        val error = withContext(Dispatchers.IO) { runCatching(work).exceptionOrNull() }
        done(error)
    }

    val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        status = s.backingUp
        scope.launch {
            // On a new phone the picked file may already hold the old phone's trips: offer them first.
            val existing = withContext(Dispatchers.IO) { DriveBackup.existingBackup(ctx, uri) }
            if (existing != null) {
                pendingLink = uri
                pendingRestore = existing
                status = s.driveExisting
                return@launch
            }
            io({ DriveBackup.link(ctx, uri) }) { e ->
                drive = DriveBackup.state(ctx)
                status = e?.let { s.backupFailed.format(it.message) } ?: s.driveDone
            }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        io({
            (ctx.contentResolver.openOutputStream(uri) ?: error("?")).bufferedWriter().use { it.write(Backup.create(ctx)) }
        }) { e -> status = e?.let { s.backupFailed.format(it.message) } ?: s.fileSaved }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        var text: String? = null
        io({ text = DriveBackup.read(ctx, uri) }) { e ->
            if (e == null && text?.let(Backup::tripCount) != null) pendingRestore = text
            else status = s.backupFailed.format(e?.message ?: s.notABackup)
        }
    }

    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(s.backupTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (!drive.linked) {
                Button(onClick = { linkLauncher.launch(DriveBackup.FILE_NAME) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(s.driveTurnOn)
                }
                Text(s.driveHint, fontSize = 12.sp, color = Color.Gray)
            } else {
                val at = if (drive.savedAt > 0) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(drive.savedAt)) else "-"
                Text(s.driveOnLine.format(at), color = Green, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                if (drive.error.isNotBlank()) Text(s.driveFailed.format(drive.error), color = Red, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = {
                        status = s.backingUp
                        DriveBackup.saveLater(ctx, force = true) { error ->
                            scope.launch {
                                drive = DriveBackup.state(ctx)
                                status = error?.let { s.backupFailed.format(it) } ?: s.backedUp
                            }
                        }
                    }) { Text(s.backupNow) }
                    OutlinedButton(enabled = !trip.running, onClick = {
                        var text: String? = null
                        io({ text = DriveBackup.readLinked(ctx) }) { e ->
                            if (e == null) pendingRestore = text else status = s.backupFailed.format(e.message)
                        }
                    }) { Text(s.restoreFromDrive) }
                }
                TextButton(onClick = { DriveBackup.unlink(ctx); drive = DriveBackup.state(ctx); status = "" }) {
                    Text(s.driveTurnOff, color = Red)
                }
            }

            Text(s.fileBackup, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { fileLauncher.launch(DriveBackup.FILE_NAME) }) { Text(s.makeBackupFile) }
                OutlinedButton(enabled = !trip.running, onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Text(s.restoreFile)
                }
            }
            if (trip.running) Text(s.restoreLocked, fontSize = 12.sp, color = Color.Gray)
            if (status.isNotBlank()) Text(status, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    pendingRestore?.let { text ->
        fun cancel() {
            pendingRestore = null
            // Not linked: the old backup on Drive must not be written over by this phone's data.
            if (pendingLink != null) {
                pendingLink = null
                status = s.driveNotLinked
            }
        }
        AlertDialog(
            onDismissRequest = { cancel() },
            title = { Text(s.restoreTitle) },
            text = { Text(s.restoreText.format(Backup.tripCount(text) ?: 0)) },
            confirmButton = {
                TextButton(onClick = {
                    val linkAfter = pendingLink
                    pendingRestore = null
                    pendingLink = null
                    if (TripRepo.state.value.running) { status = s.restoreLocked; return@TextButton }
                    val restored = runCatching { Backup.restore(ctx, text) }
                    restored.exceptionOrNull()?.let { status = s.backupFailed.format(it.message); return@TextButton }
                    fun reopen() {
                        Toast.makeText(ctx, s.restoredMsg.format(restored.getOrDefault(0)), Toast.LENGTH_LONG).show()
                        // Everything on screen is read again from the restored data.
                        (ctx as? Activity)?.recreate()
                    }
                    if (linkAfter != null) {
                        // The restored data now matches the Drive file, so it is safe to link it and keep it updated.
                        io({ DriveBackup.link(ctx, linkAfter) }) { reopen() }
                    } else {
                        DriveBackup.saveLater(ctx)
                        reopen()
                    }
                }) { Text(s.restoreYes, color = Red) }
            },
            dismissButton = { TextButton(onClick = { cancel() }) { Text(s.cancel) } },
        )
    }
}
