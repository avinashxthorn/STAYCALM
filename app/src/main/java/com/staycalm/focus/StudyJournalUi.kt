package com.staycalm.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun StudyJournalSection(
    date: LocalDate,
    attachments: List<StudyAttachment>,
    busy: Boolean,
    onImport: () -> Unit,
    onOpen: (StudyAttachment) -> Unit,
    onDelete: (StudyAttachment) -> Unit
) {
    Surface(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderCopy, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("My work archive", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Files stay attached to ${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Save your notes, assignments, photos, PDFs and other work. Revisit any date to open them.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Rounded.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Saving your files…" else if (date == LocalDate.now()) "Upload today's work" else "Add files to this date")
            }
            if (attachments.isEmpty()) {
                Text("No files saved for this day yet.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${attachments.size} saved file${if (attachments.size == 1) "" else "s"}",
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                attachments.sortedByDescending { it.addedAt }.forEach { file ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text("${(file.size + 1023) / 1024} KB · Stored on this device",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onOpen(file) }) {
                            Icon(Icons.Rounded.OpenInNew, "Open ${file.name}")
                        }
                        IconButton(onClick = { onDelete(file) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete ${file.name}",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
