package com.macebox.crate.data.api

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
