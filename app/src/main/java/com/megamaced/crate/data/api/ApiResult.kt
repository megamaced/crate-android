package com.megamaced.crate.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

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

suspend inline fun <T> apiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        when (e.code()) {
            401 -> ApiResult.Unauthorised
            else -> ApiResult.HttpError(e.code(), e.message())
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
        ApiResult.HttpError(-1, "Unexpected response from server.")
    } catch (e: Exception) {
        Timber.e(e, "Unexpected API error")
        ApiResult.HttpError(-1, e.message)
    }
