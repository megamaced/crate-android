package com.macebox.crate.data.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OcsConverterFactoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CrateApiService

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(OcsConverterFactory(json))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        api = retrofit.create(CrateApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getMe unwraps OCS envelope and parses MeDto`() =
        runTest {
            val body =
                """
                {
                    "ocs": {
                        "meta": {"status": "ok", "statuscode": 200, "message": "OK"},
                        "data": {
                            "userId": "alice",
                            "displayName": "Alice",
                            "avatarUrl": "/avatar/alice/64",
                            "hasDiscogsToken": true,
                            "marketCurrency": "GBP",
                            "autoFetchMarketRates": false,
                            "autoEnrichOnClick": true,
                            "autoEnrichOnImport": true
                        }
                    }
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(body).setResponseCode(200))

            val me = api.getMe()

            assertEquals("alice", me.userId)
            assertEquals("Alice", me.displayName)
            assertEquals("GBP", me.marketCurrency)
            assertTrue(me.hasDiscogsToken)
            assertTrue(me.autoEnrichOnClick)

            val request = server.takeRequest()
            assertEquals("/ocs/v2.php/apps/crate/api/v1/me", request.path)
        }

    @Test
    fun `getCurrencies unwraps envelope around primitive list`() =
        runTest {
            val body =
                """
                {
                    "ocs": {
                        "meta": {"status": "ok", "statuscode": 200, "message": "OK"},
                        "data": ["GBP","USD","EUR"]
                    }
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(body).setResponseCode(200))

            val currencies = api.getCurrencies()

            assertEquals(listOf("GBP", "USD", "EUR"), currencies)
        }

    @Test
    fun `getMedia parses paginated response`() =
        runTest {
            val body =
                """
                {
                    "ocs": {
                        "meta": {"status": "ok", "statuscode": 200},
                        "data": {
                            "items": [
                                {
                                    "id": 42,
                                    "title": "OK Computer",
                                    "artist": "Radiohead",
                                    "format": "LP",
                                    "year": 1997,
                                    "status": "owned",
                                    "category": "music"
                                }
                            ],
                            "total": 1,
                            "limit": 50,
                            "offset": 0
                        }
                    }
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(body).setResponseCode(200))

            val page = api.getMedia(category = "music")

            assertEquals(1, page.total)
            assertEquals(50, page.limit)
            assertEquals(1, page.items.size)
            assertEquals("OK Computer", page.items[0].title)
            assertEquals("Radiohead", page.items[0].artist)
        }
}
