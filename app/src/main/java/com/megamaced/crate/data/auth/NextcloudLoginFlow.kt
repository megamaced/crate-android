package com.megamaced.crate.data.auth

import com.megamaced.crate.R
import com.megamaced.crate.util.UiText
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LoginFlowInitResponse(
    val poll: PollEndpoint,
    val login: String,
)

@Serializable
data class PollEndpoint(
    val token: String,
    val endpoint: String,
)

@Serializable
data class LoginFlowResult(
    val server: String,
    val loginName: String,
    val appPassword: String,
)

sealed interface LoginFlowStatus {
    data object Polling : LoginFlowStatus

    data class Success(
        val result: LoginFlowResult,
    ) : LoginFlowStatus

    data class Error(
        val reason: UiText,
    ) : LoginFlowStatus
}

@Singleton
class NextcloudLoginFlow
    @Inject
    constructor() {
        private val client = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }

        fun initiate(host: String): Result<LoginFlowInitResponse> {
            val url = "${host.trimEnd('/')}/index.php/login/v2"
            val origin = url.toHttpUrlOrNull()
                ?: return Result.failure(LoginFlowException(UiText.Res(R.string.login_error_invalid_address)))
            val request = Request
                .Builder()
                .url(url)
                .post(FormBody.Builder().build())
                .header("User-Agent", USER_AGENT)
                .build()

            return try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return Result.failure(
                        LoginFlowException(
                            UiText.Res(R.string.login_error_server_returned, listOf(response.code)),
                        ),
                    )
                }
                val body = response.body?.string()
                    ?: return Result.failure(LoginFlowException(UiText.Res(R.string.login_error_empty_response)))
                val initResponse = json.decodeFromString<LoginFlowInitResponse>(body)
                // Both URLs come from the response body, so a hostile or
                // compromised server could point them at an origin the user
                // never typed — relaying the flow token to a third party, or
                // steering the browser somewhere else entirely. Neither is
                // used unless it belongs to the origin we just POSTed to.
                if (!isSameOrigin(origin, initResponse.poll.endpoint) ||
                    !isSameOrigin(origin, initResponse.login)
                ) {
                    return Result.failure(
                        LoginFlowException(UiText.Res(R.string.login_error_foreign_login_url)),
                    )
                }
                Result.success(initResponse)
            } catch (e: Exception) {
                Timber.e(e, "Login flow initiation failed")
                Result.failure(
                    LoginFlowException(
                        UiText.Res(R.string.login_error_connect_failed, listOf(e.message.toString())),
                        e,
                    ),
                )
            }
        }

        /**
         * Polls until the browser half of the flow completes. [expectedOrigin]
         * is the address the user typed; the returned credentials are refused
         * unless the `server` they are scoped to is that same origin, so a
         * server cannot hand back an app password bound to somewhere else.
         */
        suspend fun poll(
            endpoint: String,
            token: String,
            expectedOrigin: String,
        ): LoginFlowStatus {
            val origin = expectedOrigin.toHttpUrlOrNull()
                ?: return LoginFlowStatus.Error(UiText.Res(R.string.login_error_invalid_address))
            if (!isSameOrigin(origin, endpoint)) {
                return LoginFlowStatus.Error(UiText.Res(R.string.login_error_foreign_login_url))
            }
            val body = FormBody
                .Builder()
                .add("token", token)
                .build()
            val request = Request
                .Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", USER_AGENT)
                .build()

            repeat(MAX_POLL_ATTEMPTS) {
                try {
                    val response = client.newCall(request).execute()
                    when (response.code) {
                        200 -> {
                            val responseBody = response.body?.string()
                                ?: return LoginFlowStatus.Error(UiText.Res(R.string.login_error_empty_response))
                            val result = json.decodeFromString<LoginFlowResult>(responseBody)
                            // The app password is about to be bound to whatever
                            // `server` says. Refuse an origin the user never
                            // typed: otherwise a hostile server can point the
                            // whole session — every later API call, with the
                            // Basic header attached — somewhere else.
                            if (!isSameOrigin(origin, result.server)) {
                                return LoginFlowStatus.Error(
                                    UiText.Res(R.string.login_error_foreign_credentials),
                                )
                            }
                            return LoginFlowStatus.Success(result)
                        }

                        404 -> {
                            // Not yet authorized, keep polling
                        }

                        else -> {
                            return LoginFlowStatus.Error(
                                UiText.Res(R.string.login_error_server_returned, listOf(response.code)),
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Poll attempt failed")
                }
                delay(POLL_INTERVAL_MS)
            }
            return LoginFlowStatus.Error(UiText.Res(R.string.login_error_timed_out))
        }

        /**
         * True when [candidate] parses and shares [expected]'s scheme, host and
         * port. Path is deliberately not compared — a subdirectory install
         * returns its own base path in these URLs.
         */
        private fun isSameOrigin(
            expected: HttpUrl,
            candidate: String,
        ): Boolean {
            val url = candidate.toHttpUrlOrNull() ?: return false
            return url.scheme == expected.scheme &&
                url.host == expected.host &&
                url.port == expected.port
        }

        companion object {
            private const val USER_AGENT = "Crate Android"
            private const val POLL_INTERVAL_MS = 5_000L
            private const val MAX_POLL_ATTEMPTS = 60 // 5 minutes total
        }
    }

/**
 * A login attempt that could not complete. [reason] is what the login screen
 * shows: the flow runs without a Context, so it names the wording rather than
 * spelling it out.
 */
class LoginFlowException(
    val reason: UiText,
    cause: Throwable? = null,
) : Exception(cause)
