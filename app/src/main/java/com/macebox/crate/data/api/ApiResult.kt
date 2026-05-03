package com.macebox.crate.data.api

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
    } catch (e: IOException) {
        Timber.w(e, "Network error")
        ApiResult.NetworkError
    } catch (e: Exception) {
        Timber.e(e, "Unexpected API error")
        ApiResult.HttpError(-1, e.message)
    }
