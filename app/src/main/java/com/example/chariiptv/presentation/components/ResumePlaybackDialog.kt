package com.example.chariiptv.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chariiptv.theme.MobilePrimary
import com.example.chariiptv.theme.MobileSurface

@Composable
fun ResumePlaybackDialog(
    movieTitle: String,
    savedPositionMs: Long,
    totalDurationMs: Long,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    val progressPercent = if (totalDurationMs > 0) {
        ((savedPositionMs.toFloat() / totalDurationMs.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Continuar Reproducción",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = movieTitle,
                    color = MobilePrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Habías pausado en el minuto ${formatTime(savedPositionMs)}" +
                            if (progressPercent > 0) " ($progressPercent% completado)." else ".",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = MobileSurface,
        shape = RoundedCornerShape(16.dp),
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary)
            ) {
                Text("Reanudar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onRestart) {
                Text("Desde el inicio", color = Color.LightGray)
            }
        }
    )
}
