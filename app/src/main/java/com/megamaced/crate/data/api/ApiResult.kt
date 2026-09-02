package com.megamaced.crate.data.api

import com.megamaced.crate.R
import com.megamaced.crate.util.ExifStripFailedException
import com.megamaced.crate.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/**
 * [ApiResult.HttpError.code] used when the failure never reached the HTTP
 * layer — an unexpected exception. Callers that can degrade gracefully
 * (serving a cached copy) treat it like [ApiResult.NetworkError].
 */
const val PARSE_FAILURE_CODE = -1

/**
 * [ApiResult.HttpError.code] for a response the DTO contract couldn't read.
 * Degrades like [PARSE_FAILURE_CODE] — see [NON_HTTP_FAILURE_CODES] — and is
 * separate only so the UI can word it as a contract mismatch rather than as an
 * anonymous failure.
 */
const val MALFORMED_RESPONSE_CODE = -2

/**
 * [ApiResult.HttpError.code] for an image the device could not strip metadata
 * from, which is therefore never sent. Unlike the codes above this is a
 * deliberate refusal, not a failure, so it is worded as one and never retried.
 */
const val IMAGE_NOT_SANITISED_CODE = -3

/** Every [ApiResult.HttpError.code] that never reached the HTTP layer. */
val NON_HTTP_FAILURE_CODES = setOf(PARSE_FAILURE_CODE, MALFORMED_RESPONSE_CODE)

sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    data object NetworkError : ApiResult<Nothing>

    data class HttpError(
        val code: Int,
        val message: String?,
    ) : ApiResult<Nothing>

    data object Unauthorised : ApiResult<Nothing>
}

/**
 * The best server-supplied explanation for a failed request.
 *
 * Crate's controllers answer a validation failure with an OCS envelope
 * carrying the reason in `ocs.data.error` (and sometimes `ocs.meta.message`).
 * The HTTP reason phrase that [HttpException.message] exposes is an empty
 * string over HTTP/2 — the normal case for Nextcloud behind TLS — so it is
 * only a last resort, and blank is reported as no message at all so callers
 * fall back to their own wording.
 */
fun ocsErrorMessage(e: HttpException): String? {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    return body?.let(::parseOcsError) ?: e.message().orEmpty().ifBlank { null }
}

/**
 * How a failed request reads to the user: the server's own explanation when it
 * sent one, our own wording otherwise.
 */
fun ApiResult.HttpError.toUiText(): UiText =
    when {
        message != null -> UiText.Raw(message)
        code == MALFORMED_RESPONSE_CODE -> UiText.Res(R.string.error_unexpected_response)
        code == IMAGE_NOT_SANITISED_CODE -> UiText.Res(R.string.error_image_not_sanitised)
        else -> UiText.Res(R.string.error_server, listOf(code))
    }

private val errorBodyJson = Json { ignoreUnknownKeys = true }

private fun parseOcsError(body: String): String? =
    runCatching {
        val ocs = errorBodyJson.parseToJsonElement(body).jsonObject["ocs"]?.jsonObject
        val fromData = (ocs?.get("data") as? JsonObject)?.stringOrNull("error")
        val fromMeta = (ocs?.get("meta") as? JsonObject)?.stringOrNull("message")
        fromData ?: fromMeta
    }.getOrNull()

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

suspend inline fun <T> apiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        when (e.code()) {
            401 -> ApiResult.Unauthorised
            else -> ApiResult.HttpError(e.code(), ocsErrorMessage(e))
        }
    } catch (e: OcsException) {
        // OCS 997 == "not authenticated"; treat it like an HTTP 401.
        when (e.statusCode) {
            401, 997 -> ApiResult.Unauthorised
            else -> ApiResult.HttpError(e.statusCode, e.message)
        }
    } catch (e: IOException) {
        Timber.w(e, "Network error")
        ApiResult.NetworkError
    } catch (e: ExifStripFailedException) {
        // The image never left the device: stripping its metadata failed, and
        // uploading it anyway would break the guarantee the app makes about
        // location data.
        Timber.w(e, "Upload refused: image could not be sanitised")
        ApiResult.HttpError(IMAGE_NOT_SANITISED_CODE, null)
    } catch (e: CancellationException) {
        // Never swallow structured-concurrency cancellation: let it propagate
        // so a cancelled viewModelScope job actually stops instead of falling
        // through to the generic handler and posting a spurious error state.
        throw e
    } catch (e: SerializationException) {
        // Response didn't match the DTO contract (missing field, type
        // mismatch, backend drift). Distinguish it from a generic failure so
        // the user sees an intelligible message instead of "Server error (-1)".
        Timber.e(e, "Failed to parse server response")
        ApiResult.HttpError(MALFORMED_RESPONSE_CODE, null)
    } catch (e: Exception) {
        Timber.e(e, "Unexpected API error")
        ApiResult.HttpError(PARSE_FAILURE_CODE, e.message)
    }
