package com.example.model

sealed interface ContextMenuData {
    data class Link(
        val url: String,
        val title: String? = null,
        val linkText: String? = null
    ) : ContextMenuData

    data class Image(
        val imageUrl: String,
        val title: String? = null
    ) : ContextMenuData

    data class ImageLink(
        val linkUrl: String,
        val imageUrl: String,
        val title: String? = null,
        val linkText: String? = null
    ) : ContextMenuData
}
