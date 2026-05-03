package com.macebox.crate.data.auth

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

        val result = loginFlow.initiate(server.url("/").toString())

        assertTrue(result.isSuccess)
        val initResponse = result.getOrThrow()
        assertEquals("test-token-123", initResponse.poll.token)
        assertTrue(initResponse.login.contains("/login/v2/flow"))
    }

    @Test
    fun `initiate returns failure on server error`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = loginFlow.initiate(server.url("/").toString())

        assertTrue(result.isFailure)
    }

    @Test
    fun `poll returns Success when server responds with credentials`() =
        runTest {
            val responseBody =
                """
                {
                    "server": "https://cloud.example.com",
                    "loginName": "testuser",
                    "appPassword": "secret-app-password"
                }
                """.trimIndent()

            server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

            val status = loginFlow.poll(
                endpoint = server.url("/login/v2/poll").toString(),
                token = "test-token",
            )

            assertTrue(status is LoginFlowStatus.Success)
            val success = status as LoginFlowStatus.Success
            assertEquals("https://cloud.example.com", success.result.server)
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
                            "server": "https://cloud.example.com",
                            "loginName": "user",
                            "appPassword": "pass"
                        }
                        """.trimIndent(),
                    ).setResponseCode(200),
            )

            val status = loginFlow.poll(
                endpoint = server.url("/login/v2/poll").toString(),
                token = "test-token",
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
            )

            assertTrue(status is LoginFlowStatus.Error)
        }
}
