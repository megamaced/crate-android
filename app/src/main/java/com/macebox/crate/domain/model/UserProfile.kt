package com.macebox.crate.domain.model

data class UserProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val hasDiscogsToken: Boolean,
    val marketCurrency: String?,
    val autoFetchMarketRates: Boolean,
    val autoEnrichOnClick: Boolean,
    val autoEnrichOnImport: Boolean,
    val crateVersion: String?,
)

data class MarketSettings(
    val autoFetchMarketRates: Boolean,
    val marketCurrency: String,
    val autoEnrichOnClick: Boolean = true,
    val autoEnrichOnImport: Boolean = true,
)

data class RefreshableMarketValues(
    val currency: String,
    val itemIds: List<Long>,
)
