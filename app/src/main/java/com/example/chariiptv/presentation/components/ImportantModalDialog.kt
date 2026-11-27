package com.example.chariiptv.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.chariiptv.R
import com.example.chariiptv.theme.MobileBackground
import com.example.chariiptv.theme.MobilePrimary
import com.example.chariiptv.theme.MobileSurface
import com.example.chariiptv.theme.MobileSurfaceFocused

@Composable
fun ImportantModalDialog(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.88f)
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = MobileBackground,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ℹ️ Información Importante",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¡Muchas gracias por sus donaciones! Esta app es 100% gratuita y de libre acceso.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tarjeta 1: Donaciones PayPal (Vertical con QR y leyenda debajo)
                QrDonationCard(
                    title = "💳 PayPal",
                    subtitle = "Internacional",
                    qrDrawableId = R.drawable.qr_paypal,
                    qrDescription = "PayPal QR",
                    legend = "Donación voluntaria con tarjeta o saldo PayPal.",
                    linkText = "paypal.com",
                    accentColor = Color(0xFF38BDF8)
                )

                // Tarjeta 2: Donaciones Yape (Vertical con QR y leyenda debajo)
                QrDonationCard(
                    title = "📱 Yape",
                    subtitle = "Perú / BCP",
                    qrDrawableId = R.drawable.qr_yape,
                    qrDescription = "Yape QR",
                    legend = "Escanea directamente desde la app Yape.",
                    linkText = "Yape (Perú)",
                    accentColor = Color(0xFFC084FC)
                )

                // Tarjeta 3: Web Oficial del Desarrollador (Vertical con QR y leyenda debajo)
                QrDonationCard(
                    title = "🌐 Web Oficial",
                    subtitle = "Desarrollador",
                    qrDrawableId = R.drawable.qr_web,
                    qrDescription = "Web QR",
                    legend = "Novedades, reportes y contacto directo.",
                    linkText = "www.qwertyaserty.com",
                    accentColor = Color(0xFF34D399)
                )

                // Cuadro Legal y Exención de Responsabilidad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MobileSurface)
                        .border(BorderStroke(1.dp, Color(0x22FFFFFF)), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "⚖️ Aviso Legal, Propiedad Intelectual y Exención de Responsabilidad:",
                            color = MobilePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Naturaleza del Software: Chari-IPTV es un reproductor multimedia cliente de libre acceso. No almacena, gestiona, transmite ni retransmite señales o contenidos en servidores propios.\n" +
                                   "• Señales en Vivo (IPTV): Los enlaces provienen íntegramente de repositorios e índices públicos abiertos en internet (iptv-org, TDTChannels). El desarrollador no controla la estabilidad ni emisión de las señales.\n" +
                                   "• Catálogo Ghibli: El apartado VOD indexa enlaces de preservación cultural pública alojados externamente en Internet Archive (archive.org). Ningún archivo de video reside en la app.\n" +
                                   "• Derechos de Autor y Marcas: Todas las marcas, logotipos, nombres y producciones pertenecen a Studio Ghibli Inc. (Hayao Miyazaki, Isao Takahata) y a sus respectivas cadenas televisivas.\n" +
                                   "• Disponibilidad: El autor no se responsabiliza por restricciones geográficas, servidores fuera de línea o cambios en enlaces de terceros.",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Justify
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MobilePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entendido / Cerrar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun QrDonationCard(
    title: String,
    subtitle: String,
    qrDrawableId: Int,
    qrDescription: String,
    legend: String,
    linkText: String,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MobileSurface)
            .border(BorderStroke(1.dp, Color(0x28FFFFFF)), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título del Método
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Código QR centrado
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = qrDrawableId),
                contentDescription = qrDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Leyenda debajo del código QR
        Text(
            text = legend,
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = linkText,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
