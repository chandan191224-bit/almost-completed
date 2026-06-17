package com.example.db

data class DocEntity(
    val id: Int = 0,
    val title: String,
    val type: String, // "word", "sheet", "slide"
    val content: String,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val layoutJson: String = "",
    val folderName: String? = null,
    val isDeleted: Boolean = false
)
