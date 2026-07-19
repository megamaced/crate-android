package com.megamaced.crate.data.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

@Serializable
data class OcsEnvelope<T>(
    val ocs: OcsBody<T>,
)

@Serializable
data class OcsBody<T>(
    val meta: OcsMeta? = null,
    val data: T,
)

@Serializable
data class OcsMeta(
    val status: String? = null,
    val statuscode: Int? = null,
    val message: String? = null,
)

annotation class OcsResponse

/**
 * Thrown when an OCS envelope arrives with HTTP 200 but a failure
 * `meta.statuscode`. [apiCall] maps it to [ApiResult.HttpError]/[ApiResult.Unauthorised]
 * so a 200-with-error body isn't mistaken for success.
 */
class OcsException(
    val statusCode: Int,
    override val message: String?,
) : RuntimeException(message)

class OcsConverterFactory(
    private val json: Json,
) : Converter.Factory() {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (annotations.none { it is OcsResponse }) return null

        val envelopeType = TypeToken.parameterized(OcsEnvelope::class.java, type)

        @Suppress("UNCHECKED_CAST")
        val serializer = json.serializersModule.serializer(envelopeType) as KSerializer<OcsEnvelope<Any?>>

        return Converter<ResponseBody, Any?> { body ->
            val envelope = json.decodeFromString(serializer, body.string())
            // OCS carries the real outcome in meta.statuscode (100 for v1
            // success, 2xx for v2). A non-success code on an HTTP-200 envelope
            // is a failure the transport layer didn't surface — reject it
            // rather than hand callers empty/partial data as "success".
            val code = envelope.ocs.meta?.statuscode
            if (code != null && code != 100 && code !in 200..299) {
                throw OcsException(code, envelope.ocs.meta?.message)
            }
            envelope.ocs.data
        }
    }
}

private object TypeToken {
    fun parameterized(
        rawType: Class<*>,
        vararg typeArgs: Type,
    ): ParameterizedType =
        object : ParameterizedType {
            override fun getRawType(): Type = rawType

            override fun getActualTypeArguments(): Array<Type> = typeArgs.toList().toTypedArray()

            override fun getOwnerType(): Type? = null
        }
}
