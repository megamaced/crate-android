package com.macebox.crate.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val hasDiscogsToken: Boolean = false,
    val marketCurrency: String? = null,
    val autoFetchMarketRates: Boolean = false,
    val autoEnrichOnClick: Boolean = false,
    val autoEnrichOnImport: Boolean = false,
)

@Serializable
data class HasTokenDto(
    val hasToken: Boolean,
    val token: String? = null,
)

@Serializable
data class HasKeyDto(
    val hasKey: Boolean,
    val key: String? = null,
)

@Serializable
data class TokenRequest(
    val token: String,
)

@Serializable
data class KeyRequest(
    val key: String,
)

@Serializable
data class MarketSettingsDto(
    val autoFetchMarketRates: Boolean,
    val marketCurrency: String,
)

@Serializable
data class CurrencyRequest(
    val currency: String,
)

@Serializable
data class CurrencyResponseDto(
    val marketCurrency: String,
)
