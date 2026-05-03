package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshAllDto(
    val currency: String,
    val total: Int,
    val itemIds: List<Long> = emptyList(),
)
