package dev.shivampingale.vcport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun VaultPane(
    modifier: Modifier = Modifier,
    dirPath: String,
    entries: List<VaultEntry>,
    selectedNames: Set<String>,
    truncated: Boolean,
    busy: Boolean,
    mounts: List<MountedVolume>,
    activeMount: Int,
    readOnly: Boolean,
    onSelectMount: (Int) -> Unit,
    onDismountMount: (Int) -> Unit,
    onOpenAnother: () -> Unit,
    hashResult: String = "",
    canTransfer: Boolean,
    onCopyToVolume: () -> Unit,
    onMoveToVolume: () -> Unit,
    onUp: () -> Unit,
    onGoToPath: (String) -> Unit,
    onOpen: (VaultEntry) -> Unit,
    onPreview: () -> Unit,
    onShare: (List<VaultEntry>) -> Unit,
    onCopyFromDevice: () -> Unit,
    onMoveFromDevice: () -> Unit,
    onCopyToDevice: () -> Unit,
    onMoveToDevice: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onHashSelected: () -> Unit,
    onWipeFreeSpace: () -> Unit,
    onSelectAll: () -> Unit,
    onMore: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fileCount = entries.count { !it.isDir }
    val allFilesSelected = fileCount > 0 && entries.filter { !it.isDir }.all { it.name in selectedNames }
    val live = mounts.isNotEmpty()
    var folderMenu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Text(
            if (mounts.size > 1) "${mounts.size} volumes mounted" else "Mounted in this app",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Text(
            "Slots are this session only. Not a system drive. Select files, then Copy to volume / Copy to device, or Copy from device.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (readOnly) {
            Text(
                "Read-only. This slot refuses writes (wipe, import, delete, rename).",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("read_only_banner")
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenAnother, enabled = !busy) { Text("Open another container") }
            if (BuildConfig.ENABLE_IN_APP_PREVIEW) {
                TextButton(
                    onClick = onPreview,
                    enabled = !busy && live,
                    modifier = Modifier.testTag("view_in_app")
                ) { Text("View in app") }
            }
            TextButton(onClick = onSelectAll, enabled = !busy && live && fileCount > 0) {
                Text(if (allFilesSelected) "Clear selection" else "Select files")
            }
            if (canTransfer) {
                TextButton(
                    onClick = onCopyToVolume,
                    enabled = !busy && live,
                    modifier = Modifier.testTag("copy_to_volume")
                ) { Text("Copy to volume") }
                TextButton(
                    onClick = onMoveToVolume,
                    enabled = !busy && live,
                    modifier = Modifier.testTag("move_to_volume")
                ) { Text("Move to volume") }
            }
            TextButton(
                onClick = onNewFolder,
                enabled = !busy && live,
                modifier = Modifier.testTag("new_folder")
            ) { Text("New folder") }
            TextButton(
                onClick = onWipeFreeSpace,
                enabled = !busy && live,
                modifier = Modifier.testTag("wipe_free_space")
            ) { Text("Wipe free space") }
            TextButton(
                onClick = onHashSelected,
                enabled = !busy && live,
                modifier = Modifier.testTag("hash_in_volume")
            ) { Text("SHA-256 in volume") }
            Box {
                TextButton(onClick = { folderMenu = true }, enabled = !busy && live) { Text("Folder") }
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy from device") },
                        onClick = { folderMenu = false; onCopyFromDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move from device") },
                        onClick = { folderMenu = false; onMoveFromDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to device") },
                        onClick = { folderMenu = false; onCopyToDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to device") },
                        onClick = { folderMenu = false; onMoveToDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { folderMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { folderMenu = false; onDelete() }
                    )
                    DropdownMenuItem(
                        text = { Text("Properties") },
                        onClick = { folderMenu = false; onProperties() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share decrypted") },
                        onClick = {
                            folderMenu = false
                            onShare(entries.filter { it.name in selectedNames && !it.isDir })
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("No.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
                    Text("Volume", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                for (slot in 0 until MOUNT_SLOTS) {
                    val vol = mounts.getOrNull(slot)
                    val selected = vol != null && slot == activeMount
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selected) colors.primaryContainer else Color.Transparent)
                            .clickable(enabled = !busy) {
                                if (vol != null) onSelectMount(slot) else onOpenAnother()
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .testTag("mount_slot_$slot"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${slot + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            vol?.label ?: "Empty",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (vol == null) colors.onSurfaceVariant else colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (vol != null) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismount ${vol.label}",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(enabled = !busy) { onDismountMount(slot) }
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.outline.copy(alpha = 0.4f))
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
        if (hashResult.isNotEmpty()) {
            Text(
                hashResult,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("hash_result")
            )
        }
        Row(
            Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dirPath.isNotEmpty()) {
                OutlinedButton(onClick = onUp, enabled = !busy, modifier = Modifier.testTag("vault_up")) { Text("Up") }
                Spacer(Modifier.padding(8.dp))
            }
            val parts = dirPath.split('/').filter { it.isNotEmpty() }
            TextButton(
                onClick = { onGoToPath("") },
                enabled = !busy && dirPath.isNotEmpty()
            ) { Text("/") }
            parts.forEachIndexed { index, part ->
                Text("›", color = colors.onSurfaceVariant)
                val target = parts.take(index + 1).joinToString("/")
                TextButton(
                    onClick = { onGoToPath(target) },
                    enabled = !busy && index < parts.lastIndex
                ) { Text(part) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Name", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Size", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        if (entries.isEmpty() && !busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (!live) "No volume in this slot." else "This folder is empty.",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (!live) {
                        "Open volume on the Volume tab, or tap an empty slot. This is not a system drive."
                    } else {
                        "Tap a folder to open it, or Copy from device to add files. Copy to device writes files Files can open."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("vault_list"),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            itemsIndexed(entries, key = { index, entry -> "$index:${entry.name}" }) { _, entry ->
                val selected = entry.name in selectedNames
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) colors.primaryContainer else colors.surface)
                        .clickable(enabled = !busy) { onOpen(entry) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(28.dp)
                            .background(
                                if (selected) colors.primary else colors.surfaceVariant,
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                if (entry.isDir) "▸" else "•",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entry.isDir) {
                            Text(
                                "Folder — tap to open",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (!entry.isDir) {
                        Text(
                            formatSize(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
            }
            if (truncated) {
                item {
                    OutlinedButton(
                        onClick = onMore,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) { Text("Load more") }
                }
            }
        }
            }
        }
    }
}

internal fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    if (size < 1024L * 1024 * 1024) return "${size / (1024 * 1024)} MB"
    return "${size / (1024L * 1024 * 1024)} GB"
}
