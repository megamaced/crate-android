package com.megamaced.crate.data.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

@Serializable
data class HomeFeedDto(
    @Serializable(with = CategoryFeedMapSerializer::class)
    val categories: Map<String, CategoryFeedDto> = emptyMap(),
    val recentlyAdded: List<MediaItemDto> = emptyList(),
    val mostValuable: List<MediaItemDto> = emptyList(),
)

@Serializable
data class CategoryFeedDto(
    val count: Int = 0,
    val itemOfDay: MediaItemDto? = null,
    val recentItems: List<MediaItemDto> = emptyList(),
)

/**
 * Decodes `home.categories` from either shape the server can produce.
 *
 * PHP has one array type, so an associative array with no entries encodes as
 * `[]` rather than `{}` — which is exactly what a user with no owned,
 * non-hidden items gets. A plain map serializer rejects that, turning an
 * empty collection into a hard parse failure on the Home screen, so an array
 * is read as "no categories" instead.
 */
internal object CategoryFeedMapSerializer : KSerializer<Map<String, CategoryFeedDto>> {
    private val delegate = MapSerializer(String.serializer(), CategoryFeedDto.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Map<String, CategoryFeedDto> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = input.decodeJsonElement()) {
            is JsonObject -> input.json.decodeFromJsonElement(delegate, element)
            is JsonArray -> emptyMap()
            else -> emptyMap()
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: Map<String, CategoryFeedDto>,
    ) = delegate.serialize(encoder, value)
}
