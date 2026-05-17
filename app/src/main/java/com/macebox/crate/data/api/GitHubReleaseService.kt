package com.macebox.crate.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface GitHubReleaseService {
    @GET("repos/megamaced/crate-android/releases/latest")
    suspend fun latestRelease(): GitHubReleaseDto
}

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("prerelease") val preRelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
)
