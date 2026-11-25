package com.example.chariiptv.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chariiptv.remote.DiscoveredTv
import com.example.chariiptv.remote.TvStatus
import com.example.chariiptv.theme.MobileBackground
import com.example.chariiptv.theme.MobilePrimary
import com.example.chariiptv.theme.MobileSurface
import com.example.chariiptv.theme.MobileSurfaceFocused

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualRemoteSheet(
    selectedTv: DiscoveredTv?,
    discoveredTvs: List<DiscoveredTv>,
    isScanning: Boolean,
    tvStatus: TvStatus?,
    isSyncing: Boolean,
    onSelectTv: (DiscoveredTv) -> Unit,
    onRescan: () -> Unit,
    onSendKey: (String) -> Unit,
    onStopPlayback: () -> Unit,
    onSyncFavorites: () -> Unit,
    onSendSearchText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showDevicePicker by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MobileBackground,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Gray.copy(alpha = 0.5f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Selector de Dispositivo TV
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showDevicePicker = !showDevicePicker },
                color = MobileSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MobileSurfaceFocused)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (selectedTv != null) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedTv?.displayName ?: "Sin TV seleccionada",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedTv != null) "Conectado por LAN: ${selectedTv.host}:${selectedTv.port}" else "Toca para buscar televisiones",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MobilePrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onRescan,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Escanear", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Lista desplegable de TVs encontradas
            AnimatedVisibility(visible = showDevicePicker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MobileSurfaceFocused.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    if (discoveredTvs.isEmpty()) {
                        Text(
                            text = if (isScanning) "Buscando TVs en la misma red Wi-Fi..." else "No se detectaron TVs. Asegúrate de tener Chari-IPTV abierta en tu tele.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        discoveredTvs.forEach { tv ->
                            val isCurrent = tv.host == selectedTv?.host && tv.port == selectedTv?.port
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) MobilePrimary.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        onSelectTv(tv)
                                        showDevicePicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = if (isCurrent) MobilePrimary else Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(tv.displayName, color = Color.White, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                    Text("${tv.host}:${tv.port}", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Tarjeta de Estado Actual en TV
            if (tvStatus?.isPlaying == true) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MobilePrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MobilePrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📺", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tvStatus.channelName ?: "Reproduciendo en TV",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "En pantalla grande",
                                color = MobilePrimary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStopPlayback()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Detener", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Gran Cruceta Direccional (D-Pad Táctil)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(MobileSurface)
                    .border(2.dp, MobileSurfaceFocused, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Botón ARRIBA
                DPadArrowButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("DPAD_UP")
                    }
                )

                // Botón ABAJO
                DPadArrowButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("DPAD_DOWN")
                    }
                )

                // Botón IZQUIERDA
                DPadArrowButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("DPAD_LEFT")
                    }
                )

                // Botón DERECHA
                DPadArrowButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("DPAD_RIGHT")
                    }
                )

                // Botón CENTRAL (OK / SELECT)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MobilePrimary)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = Color.White)
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSendKey("DPAD_CENTER")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Barra de Navegación del Sistema (Atrás, Inicio, Play/Pause, Mute)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Atrás",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("BACK")
                    }
                )
                RemoteCircleButton(
                    icon = Icons.Default.Home,
                    label = "Inicio",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("HOME")
                    }
                )
                RemoteCircleButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Play/Pausa",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("PLAY_PAUSE")
                    }
                )
                RemoteCircleButton(
                    icon = Icons.Default.VolumeMute,
                    label = "Silenciar",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("MUTE")
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Control de Volumen TV (Vol - / Vol +)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("VOL_DOWN")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MobileSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolumeDown, contentDescription = "Bajar volumen", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vol -", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendKey("VOL_UP")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MobileSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Subir volumen", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vol +", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Acciones Especiales: Sincronizar Favoritos y Escribir en TV
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSyncFavorites()
                    },
                    enabled = !isSyncing && selectedTv != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MobilePrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sincronizar Favs", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { showSearchDialog = true },
                    enabled = selectedTv != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, tint = MobilePrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Escribir en TV", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Diálogo para enviar texto escrito con el teclado del celular a la TV
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Escribir en la TV", color = Color.White) },
            text = {
                Column {
                    Text("El texto ingresado se buscará directamente en la televisión:", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Canal, película o país...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MobileSurface,
                            unfocusedContainerColor = MobileSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            containerColor = MobileBackground,
            confirmButton = {
                Button(
                    onClick = {
                        if (searchText.isNotBlank()) {
                            onSendSearchText(searchText)
                            showSearchDialog = false
                            searchText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary)
                ) {
                    Text("Enviar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
private fun DPadArrowButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = MobilePrimary)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun RemoteCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = 24.dp)
        ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MobileSurfaceFocused)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
