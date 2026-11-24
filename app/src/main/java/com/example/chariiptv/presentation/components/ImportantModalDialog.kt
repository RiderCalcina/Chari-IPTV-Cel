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
                    text = "¡Muchas gracias por su apoyo! Esta app es 100% gratuita y de libre acceso.",
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
                    title = "💳 PayPal (Internacional)",
                    subtitle = "Tarjetas de crédito, débito o saldo PayPal",
                    qrDrawableId = R.drawable.qr_paypal,
                    qrDescription = "PayPal QR",
                    legend = "Escanea con tu cámara móvil o app de PayPal para donaciones voluntarias internacionales que ayudan al mantenimiento de los servidores.",
                    linkText = "paypal.com",
                    accentColor = Color(0xFF38BDF8)
                )

                // Tarjeta 2: Donaciones Yape (Vertical con QR y leyenda debajo)
                QrDonationCard(
                    title = "📱 Yape (Perú / BCP)",
                    subtitle = "Transferencias rápidas en moneda local",
                    qrDrawableId = R.drawable.qr_yape,
                    qrDescription = "Yape QR",
                    legend = "Escanea directamente desde la app de Yape en tu teléfono para donar de forma segura en Perú.",
                    linkText = "Yape (Perú)",
                    accentColor = Color(0xFFC084FC)
                )

                // Tarjeta 3: Web Oficial del Desarrollador (Vertical con QR y leyenda debajo)
                QrDonationCard(
                    title = "🌐 Sitio Web Oficial",
                    subtitle = "Actualizaciones, novedades y soporte",
                    qrDrawableId = R.drawable.qr_web,
                    qrDescription = "Web QR",
                    legend = "Escanea para visitar nuestro sitio web oficial, descargar la versión más reciente para Android TV y Móvil, o contactar al autor.",
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
                            text = "⚖️ Aviso Legal y Exención de Responsabilidad:",
                            color = MobilePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Chari-IPTV es un reproductor multimedia cliente. No aloja, gestiona ni retransmite señales en servidores propios.\n" +
                                   "• Las transmisiones en directo provienen de repositorios públicos abiertos en internet (iptv-org, TDTChannels).\n" +
                                   "• El catálogo de Studio Ghibli indexa enlaces externos de preservación cultural pública alojados en Internet Archive (archive.org).\n" +
                                   "• Todos los derechos de marca y material audiovisual pertenecen a sus respectivos autores y cadenas televisivas.",
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
