package com.example.chariiptv.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.example.chariiptv.theme.MobileBackground
import com.example.chariiptv.theme.MobilePrimary
import com.example.chariiptv.theme.MobileSurface
import com.example.chariiptv.theme.MobileSurfaceFocused
import com.example.chariiptv.util.Constants
import com.google.android.gms.cast.framework.CastButtonFactory

private data class NavCategory(
    val id: String,
    val name: String,
    val icon: String,
    val isSpecial: Boolean = false
)

private val NAV_CATEGORIES = listOf(
    NavCategory("important", "Importante", "🌟", isSpecial = true),
    NavCategory("fav", "Favoritos", "⭐"),
    NavCategory("countries", "Países", "🌎"),
    NavCategory("sports", "Deportes", "⚽"),
    NavCategory("news", "Noticias", "📰"),
    NavCategory("movies", "Películas", "🎬"),
    NavCategory("series", "Series", "📺"),
    NavCategory("animation", "Infantil", "👶"),
    NavCategory("music", "Música", "🎵"),
    NavCategory("ghibli", "Studio Ghibli", "🎋")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilterId: String,
    onFilterSelected: (String) -> Unit,
    selectedCountryCode: String,
    onCountrySelected: (String) -> Unit,
    onImportantClick: () -> Unit = {},
    onOpenRemote: () -> Unit = {},
    hasDiscoveredTv: Boolean = false,
    rssNews: String,
    modifier: Modifier = Modifier
) {
    var isSearchActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MobileBackground)
            .statusBarsPadding()
    ) {
        // Fila Principal: Logo / Título o Barra de Búsqueda + Cast Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Buscar canal o película...", color = Color.Gray, fontSize = 14.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MobileSurface,
                        unfocusedContainerColor = MobileSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MobilePrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MobilePrimary)
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            } else {
                                isSearchActive = false
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.LightGray)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chari-IPTV",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MobilePrimary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v1.2",
                            color = MobilePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = { isSearchActive = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Botón de Control Remoto Virtual LAN para TV
            IconButton(
                onClick = onOpenRemote,
                modifier = Modifier.size(40.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (hasDiscoveredTv) {
                            Badge(
                                containerColor = Color(0xFF10B981),
                                modifier = Modifier.size(7.dp)
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Control Remoto TV",
                        tint = if (hasDiscoveredTv) MobilePrimary else Color.White
                    )
                }
            }

            // Botón de Google Cast
            AndroidView(
                modifier = Modifier.size(40.dp),
                factory = { ctx ->
                    MediaRouteButton(ctx).apply {
                        try {
                            CastButtonFactory.setUpMediaRouteButton(ctx, this)
                        } catch (_: Exception) {}
                    }
                }
            )
        }

        // Ticker RSS si hay noticias activas
        if (rssNews.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MobileSurface.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔴 ",
                    fontSize = 12.sp
                )
                Text(
                    text = rssNews,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            velocity = 28.dp
                        )
                )
            }
        }

        // Fila Desplazable de Categorías
        val selectedCatIndex = NAV_CATEGORIES.indexOfFirst { it.id == selectedFilterId }.coerceAtLeast(0)
        ScrollableTabRow(
            selectedTabIndex = selectedCatIndex,
            containerColor = MobileBackground,
            contentColor = Color.White,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                if (selectedCatIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedCatIndex]),
                        color = MobilePrimary,
                        height = 3.dp
                    )
                }
            },
            divider = {}
        ) {
            NAV_CATEGORIES.forEachIndexed { index, cat ->
                val isSelected = cat.id == selectedFilterId
                Tab(
                    selected = isSelected,
                    onClick = {
                        if (cat.id == "important") {
                            onImportantClick()
                        } else {
                            onFilterSelected(cat.id)
                        }
                    },
                    text = {
                        if (cat.isSpecial) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33F59E0B))
                                    .border(BorderStroke(1.5.dp, Color(0xFFF59E0B)), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🌟 Importante",
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                text = "${cat.icon} ${cat.name}",
                                color = if (isSelected) MobilePrimary else Color.LightGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        // Submenú horizontal si la pestaña es "Países"
        AnimatedVisibility(visible = selectedFilterId == "countries") {
            val countryCodes = Constants.SPANISH_COUNTRIES.toList().sorted()
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MobileSurface.copy(alpha = 0.4f))
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(countryCodes) { code ->
                    val isSelected = code == selectedCountryCode
                    val flag = Constants.COUNTRY_FLAGS[code] ?: "🏳️"
                    val countryName = Constants.SUPPORTED_COUNTRIES[code] ?: code.uppercase()
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MobilePrimary else MobileSurfaceFocused)
                            .clickable { onCountrySelected(code) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$flag $countryName",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
