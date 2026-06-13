package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.audio.TtsPlaybackState

@Composable
fun PronunciationControlRow(
    playbackState: TtsPlaybackState,
    onTogglePlayback: () -> Unit,
    onSpeakFromStart: () -> Unit,
    modifier: Modifier = Modifier,
    playbackContentDescription: String = "Start or pause playback",
    speakContentDescription: String = "Play from start",
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlayback) {
            Icon(
                when (playbackState) {
                    TtsPlaybackState.PLAYING -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = playbackContentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onSpeakFromStart) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = speakContentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
