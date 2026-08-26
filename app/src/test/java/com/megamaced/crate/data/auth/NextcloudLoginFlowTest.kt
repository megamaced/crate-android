package com.megamaced.crate.data.auth

import com.megamaced.crate.R
import com.megamaced.crate.util.UiText
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextcloudLoginFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var loginFlow: NextcloudLoginFlow

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        loginFlow = NextcloudLoginFlow()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun origin() = server.url("/").toString()

    @Test
    fun `initiate returns login URL and poll endpoint on success`() {
        val pollUrl = server.url("/login/v2/poll")
        val flowUrl = server.url("/login/v2/flow")
        val responseBody =
            """
            {
                "poll": {
                    "token": "test-token-123",
                    "endpoint": "$pollUrl"
                },
                "login": "$flowUrl"
            }
            """.trimIndent()

        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = loginFlow.initiate(origin())

        assertTrue(result.isSuccess)
        val initResponse = result.getOrThrow()
        assertEquals("test-token-123", initResponse.poll.token)
        assertTrue(initResponse.login.contains("/login/v2/flow"))
    }

    @Test
    fun `initiate returns failure on server error`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = loginFlow.initiate(origin())

        assertTrue(result.isFailure)
    }

    @Test
    fun `initiate rejects a poll endpoint on a different origin`() {
        val responseBody =
            """
            {
                "poll": {
                    "token": "test-token-123",
                    "endpoint": "https://attacker.example/poll"
                },
                "login": "${server.url("/login/v2/flow")}"
            }
            """.trimIndent()

        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = loginFlow.initiate(origin())

        assertTrue(result.isFailure)
        assertEquals(
            UiText.Res(R.string.login_error_foreign_login_url),
            (result.exceptionOrNull() as LoginFlowException).reason,
        )
    }

    @Test
    fun `initiate rejects a login URL on a different origin`() {
        val responseBody =
            """
            {
                "poll": {
                    "token": "test-token-123",
                    "endpoint": "${server.url("/login/v2/poll")}"
                },
                "login": "https://attacker.example/flow"
            }
            """.trimIndent()

        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val result = loginFlow.initiate(origin())

        assertTrue(result.isFailure)
    }

    @Test
    fun `poll returns Success when server responds with credentials`() =
        runTest {
            val responseBody =
                """
                {
                    "server": "${origin().trimEnd('/')}",
                    "loginName": "testuser",
                    "appPassword": "secret-app-password"
                }
                """.trimIndent()

            server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            val status = loginFlow.poll(
                endpoint = server.url("/login/v2/poll").toString(),
                token = "test-token",
                expectedOrigin = origin(),
            )

            assertTrue(status is LoginFlowStatus.Success)
            val success = status as LoginFlowStatus.Success
            assertEquals("testuser", success.result.loginName)
            assertEquals("secret-app-password", success.result.appPassword)
        }

    @Test
    fun `poll returns Success after initial 404 responses`() =
        runTest {
            // First two attempts return 404 (not yet authorised)
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            // Third attempt succeeds
            server.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                            "server": "${origin().trimEnd('/')}",
                            "loginName": "user",
                            "appPassword": "pass"
                        }
                        """.trimIndent(),
                    ).setResponseCode(200),
            )

            val status = loginFlow.poll(
                endpoint = server.url("/login/v2/poll").toString(),
                token = "test-token",
                expectedOrigin = origin(),
            )

            assertTrue(status is LoginFlowStatus.Success)
        }

    @Test
    fun `poll returns Error on unexpected status code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403))

            val status = loginFlow.poll(
                endpoint = server.url("/login/v2/poll").toString(),
                token = "test-token",
                expectedOrigin = origin(),
            )

            assertTrue(status is LoginFlowStatus.Error)
        }

    @Test
    fun `poll rejects credentials scoped to a different server`() =
        runTest {
            val responseBody =
                """
                {
                    "server": "https://attacker.example",
                    "loginName": "testuser",
                    "appPassword": "secret-app-password"
                }
                """.trimIndent()

            server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            val status = loginFlow.poll(
                endpoint = server.url("/login/v2/poll").toString(),
                token = "test-token",
                expectedOrigin = origin(),
            )

            assertTrue(status is LoginFlowStatus.Error)
            assertEquals(
                UiText.Res(R.string.login_error_foreign_credentials),
                (status as LoginFlowStatus.Error).reason,
            )
        }

    @Test
    fun `poll refuses an endpoint on a different origin without making a request`() =
        runTest {
            val status = loginFlow.poll(
                endpoint = "https://attacker.example/poll",
                token = "test-token",
                expectedOrigin = origin(),
            )

            assertTrue(status is LoginFlowStatus.Error)
            assertEquals(0, server.requestCount)
        }
}
