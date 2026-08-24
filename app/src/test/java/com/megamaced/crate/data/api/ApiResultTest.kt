package com.megamaced.crate.data.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ApiResultTest {
    @Test
    fun `the server's own error string is surfaced`() =
        runTest {
            // OkHttp reports an empty reason phrase over HTTP/2 — the normal
            // case for Nextcloud behind TLS — so the body is the only source.
            val body =
                """
                {"ocs":{"meta":{"status":"failure","statuscode":422,"message":"422"},
                 "data":{"error":"Unsupported purchasePriceCurrency"}}}
                """.trimIndent()

            val result = apiCall<Unit> { throw httpException(422, body) }

            assertEquals(
                ApiResult.HttpError(422, "Unsupported purchasePriceCurrency"),
                result,
            )
        }

    @Test
    fun `meta message is used when data carries no error`() =
        runTest {
            val body = """{"ocs":{"meta":{"statuscode":400,"message":"Name is required"},"data":[]}}"""

            val result = apiCall<Unit> { throw httpException(400, body) }

            assertEquals(ApiResult.HttpError(400, "Name is required"), result)
        }

    @Test
    fun `an unparseable body with a blank reason phrase yields no message`() =
        runTest {
            val result = apiCall<Unit> { throw httpException(500, "<html>gateway</html>") }

            // Null rather than "", so callers fall through to their own wording
            // instead of showing a blank snackbar.
            assertEquals(ApiResult.HttpError(500, null), result)
        }

    @Test
    fun `a 401 is reported as unauthorised regardless of body`() =
        runTest {
            val result = apiCall<Unit> { throw httpException(401, """{"ocs":{"data":{"error":"nope"}}}""") }

            assertEquals(ApiResult.Unauthorised, result)
        }

    /**
     * An HTTP/2 failure: the reason phrase is empty, which is what makes the
     * response body the only place a usable message can come from.
     */
    private fun httpException(
        code: Int,
        body: String,
    ): HttpException {
        val raw =
            okhttp3.Response
                .Builder()
                .code(code)
                .message("")
                .protocol(Protocol.HTTP_2)
                .request(Request.Builder().url("https://nextcloud.invalid/").build())
                .build()
        return HttpException(
            Response.error<Unit>(body.toResponseBody("application/json".toMediaType()), raw),
        )
    }
}
