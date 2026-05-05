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
    val crateVersion: String? = null,
)

@Serializable
data class HasTokenDto(
    val hasToken: Boolean,
)

@Serializable
data class HasKeyDto(
    val hasKey: Boolean,
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
    val autoEnrichOnClick: Boolean = true,
    val autoEnrichOnImport: Boolean = true,
)

@Serializable
data class CurrencyRequest(
    val currency: String,
)

@Serializable
data class CurrencyResponseDto(
    val marketCurrency: String,
)
