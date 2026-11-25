package com.example.chariiptv.remote

data class DiscoveredTv(
    val name: String,
    val host: String,
    val port: Int,
    val model: String = "",
    val brand: String = "",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = when {
            model.isNotBlank() && brand.isNotBlank() -> "$brand $model"
            model.isNotBlank() -> model
            name.startsWith("Chari-TV-") -> name.removePrefix("Chari-TV-")
            else -> name
        }

    val baseUrl: String
        get() = "http://$host:$port"
}
