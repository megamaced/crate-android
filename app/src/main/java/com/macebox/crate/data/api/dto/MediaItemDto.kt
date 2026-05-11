package com.macebox.crate.data.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class MediaItemDto(
    val id: Long,
    val userId: String? = null,
    val title: String,
    val artist: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val status: String,
    val category: String,
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val label: String? = null,
    val country: String? = null,
    val genres: String? = null,
    val tracklist: List<TrackDto>? = null,
    val pressingNotes: String? = null,
    val discogsArtistId: String? = null,
    val artistBio: String? = null,
    val artistMembers: List<ArtistMemberDto>? = null,
    val marketValue: Double? = null,
    val marketValueLoose: Double? = null,
    val marketValueNew: Double? = null,
    val marketValueCurrency: String? = null,
    val marketValueFetchedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TrackDto(
    val position: String? = null,
    val title: String? = null,
    val duration: String? = null,
)

/**
 * Server returns artist members in either of two shapes:
 *   - a bare string `"Emma Bunton"` (current Crate backend), or
 *   - a structured object `{"name": "Emma Bunton", "active": true}`.
 * The custom serializer normalises both into [ArtistMemberDto].
 */
@Serializable(with = ArtistMemberDtoSerializer::class)
data class ArtistMemberDto(
    val name: String,
    val active: Boolean? = null,
)

internal object ArtistMemberDtoSerializer : KSerializer<ArtistMemberDto> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ArtistMemberDto") {
            element<String>("name")
            element<Boolean?>("active", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): ArtistMemberDto {
        val input = decoder as? JsonDecoder ?: error("ArtistMemberDto only supports JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> ArtistMemberDto(name = element.content)
            is JsonObject -> {
                val name = (element["name"] as? JsonPrimitive)?.content
                    ?: error("Artist member object missing 'name'")
                val active = (element["active"] as? JsonPrimitive)
                    ?.takeIf { !it.isString }
                    ?.content
                    ?.toBooleanStrictOrNull()
                ArtistMemberDto(name = name, active = active)
            }
            else -> error("Unexpected artist member JSON: $element")
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: ArtistMemberDto,
    ) {
        val output = encoder as? JsonEncoder ?: error("ArtistMemberDto only supports JSON")
        val obj =
            buildJsonObject {
                put("name", JsonPrimitive(value.name))
                if (value.active != null) {
                    put("active", JsonPrimitive(value.active))
                }
            }
        output.encodeJsonElement(obj)
    }
}

@Serializable
data class PaginatedMediaDto(
    val items: List<MediaItemDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val wipedAt: String? = null,
)

@Serializable
data class CreateMediaItemRequest(
    val title: String,
    val artist: String,
    // Nextcloud OCS reserves `format` for response-format selection; send as `mediaFormat`.
    val mediaFormat: String,
    val year: Int? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val label: String? = null,
    val country: String? = null,
    val category: String? = null,
)
