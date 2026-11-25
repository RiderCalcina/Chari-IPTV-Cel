package com.example.chariiptv.presentation

import com.example.chariiptv.domain.Channel

data class TvUiState(
    val channels: List<Channel> = emptyList(),
    val searchResults: List<Channel> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchActive: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val selectedFilterId: String = "fav",
    val selectedCountryCode: String = "pe",
    val isCountrySubMenuVisible: Boolean = false,
    val currentChannel: Channel? = null,
    val rssNews: String = "",
    val searchQuery: String = "",
    val errorMessage: String? = null
) {
    val filteredChannels: List<Channel>
        get() = if (isSearchActive && searchQuery.isNotBlank()) {
            searchResults
        } else {
            channels
        }
}
