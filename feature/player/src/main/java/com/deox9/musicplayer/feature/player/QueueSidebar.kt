// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.lyrics.SyncedLyricLine
import com.deox9.musicplayer.player.QueueEntry

/** The synced line to highlight at a given playback position, if any. */
internal fun currentSyncedLyricLine(lines: List<SyncedLyricLine>, positionMs: Long): String? {
    if (lines.isEmpty()) return null
    return lines.lastOrNull { it.timeMs <= positionMs }?.text ?: lines.firstOrNull()?.text
}

/**
 * The queue, as a panel over the current screen.
 *
 * Every reorder action used to be a text button in the row itself — "Now", "⇤", "↑",
 * "↓", "⇥", "✕" — six of them, in a 340dp panel, leaving the title about a third of
 * the width. They are all still here, in the row's overflow menu, which is also the
 * only way any of them were reachable without sight: a button labelled "⇤" is
 * announced as whatever the font calls that character.
 */
@Composable
fun QueueSidebar(
    viewModel: PlayerViewModel = hiltViewModel(),
    queue: List<QueueEntry>,
    currentIndex: Int,
    onDismiss: () -> Unit,
) {
    val playback = viewModel.playback
    val reorder = rememberQueueReorderState(queue, playback::swapQueueItems)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Labelled because it is most of the screen: unlabelled, TalkBack
                // focuses a huge nameless target that dismisses the panel.
                .clickable(onClick = onDismiss, onClickLabel = "Close queue"),
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .width(PANEL_WIDTH)
                .fillMaxHeight(),
        ) {
            Column(
                // The panel is drawn over the whole window, so it insets itself;
                // without this the header sat behind the status bar clock.
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            ) {
                QueueHeader(count = reorder.items.size, onDismiss = onDismiss)
                Spacer(modifier = Modifier.height(12.dp))

                if (reorder.items.isEmpty()) {
                    Text("Queue is empty.")
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        QueueList(
                            reorder = reorder,
                            currentIndex = currentIndex,
                            playback = playback,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            playback.clearQueue()
                            reorder.items = emptyList()
                        },
                    ) {
                        Text("Clear queue")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(count: Int, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$count ${if (count == 1) "track" else "tracks"} · long-press a row to drag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun QueueList(
    reorder: QueueReorderState,
    currentIndex: Int,
    playback: com.deox9.musicplayer.player.PlaybackConnection,
) {
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    LazyColumn {
        items(
            count = reorder.items.size,
            key = { index -> "${reorder.items[index].uri}-$index" },
        ) { index ->
            QueueRow(
                entry = reorder.items[index],
                isCurrent = index == currentIndex,
                isDragged = reorder.draggedIndex == index,
                onPlay = { playback.playQueueIndex(index) },
                onMoveToTop = { reorder.moveTo(index, 0, playback::moveQueueItem) },
                onMoveUp = { reorder.swap(index, index - 1) },
                onMoveDown = { reorder.swap(index, index + 1) },
                onMoveToBottom = {
                    reorder.moveTo(index, reorder.items.lastIndex, playback::moveQueueItem)
                },
                onRemove = {
                    playback.removeQueueIndex(index)
                    reorder.items = reorder.items.toMutableList().apply { removeAt(index) }
                },
                dragModifier = Modifier.pointerInput(reorder.items, index) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { reorder.startDrag(index) },
                        onDragEnd = { reorder.endDrag() },
                        onDragCancel = { reorder.endDrag() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            reorder.onDrag(dragAmount.y, rowHeightPx)
                        },
                    )
                },
                canMoveUp = index > 0,
                canMoveDown = index < reorder.items.lastIndex,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    isDragged: Boolean,
    onPlay: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A minimum, not a fixed height. Two lines of text at a 200% font
            // scale are taller than 72dp and were clipped by it.
            .heightIn(min = ROW_HEIGHT)
            .background(
                if (isDragged) {
                    MaterialTheme.colorScheme.primary.copy(alpha = DRAG_TINT_ALPHA)
                } else {
                    Color.Transparent
                },
            )
            .then(dragModifier)
            .clickable(onClick = onPlay, onClickLabel = "Play"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            // Replaces a "▶" prefixed to the title, which a screen reader read out as
            // part of the track's name.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Queue actions for ${entry.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QueueRowMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onPlay = onPlay,
                onMoveToTop = onMoveToTop,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onMoveToBottom = onMoveToBottom,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun QueueRowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        QueueMenuItem("Play now", enabled = true, onDismiss = onDismiss, onClick = onPlay)
        QueueMenuItem("Move to top", canMoveUp, onDismiss, onMoveToTop)
        QueueMenuItem("Move up", canMoveUp, onDismiss, onMoveUp)
        QueueMenuItem("Move down", canMoveDown, onDismiss, onMoveDown)
        QueueMenuItem("Move to bottom", canMoveDown, onDismiss, onMoveToBottom)
        QueueMenuItem("Remove from queue", enabled = true, onDismiss = onDismiss, onClick = onRemove)
    }
}

@Composable
private fun QueueMenuItem(
    label: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = {
            onClick()
            onDismiss()
        },
    )
}

/**
 * Drag-to-reorder bookkeeping.
 *
 * Pulled out of the composable because it is the only genuinely stateful part of the
 * panel: a local copy of the queue that leads the player, plus the accumulated drag
 * distance that decides when to swap. Keeping the optimistic copy matters — the queue
 * arrives back from the controller a frame or two later, and without it a dragged row
 * snaps back before settling.
 */
private class QueueReorderState(
    initial: List<QueueEntry>,
    private val onSwap: (Int, Int) -> Unit,
) {
    var items by mutableStateOf(initial)
    var draggedIndex by mutableStateOf<Int?>(null)
    private var offsetY by mutableFloatStateOf(0f)

    fun startDrag(index: Int) {
        draggedIndex = index
        offsetY = 0f
    }

    fun endDrag() {
        draggedIndex = null
        offsetY = 0f
    }

    fun onDrag(deltaY: Float, rowHeightPx: Float) {
        var index = draggedIndex ?: return
        offsetY += deltaY
        while (offsetY >= rowHeightPx && index < items.lastIndex) {
            swap(index, index + 1)
            index += 1
            offsetY -= rowHeightPx
        }
        while (offsetY <= -rowHeightPx && index > 0) {
            swap(index, index - 1)
            index -= 1
            offsetY += rowHeightPx
        }
        draggedIndex = index
    }

    fun swap(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        onSwap(fromIndex, toIndex)
        items = items.toMutableList().apply {
            val held = this[fromIndex]
            this[fromIndex] = this[toIndex]
            this[toIndex] = held
        }
    }

    fun moveTo(fromIndex: Int, toIndex: Int, onMove: (Int, Int) -> Unit) {
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        onMove(fromIndex, toIndex)
        items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun sync(queue: List<QueueEntry>) {
        items = queue
        if (draggedIndex != null && draggedIndex !in queue.indices) endDrag()
    }
}

@Composable
private fun rememberQueueReorderState(
    queue: List<QueueEntry>,
    onSwap: (Int, Int) -> Unit,
): QueueReorderState {
    val state = remember { QueueReorderState(queue, onSwap) }
    LaunchedEffect(queue) { state.sync(queue) }
    return state
}

private const val SCRIM_ALPHA = 0.35f
private const val DRAG_TINT_ALPHA = 0.12f
private val PANEL_WIDTH = 340.dp
private val ROW_HEIGHT = 72.dp
