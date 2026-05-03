package com.macebox.crate.data.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Non-OCS endpoints that return raw binary (artwork images, export files)
 * or accept multipart uploads. These bypass the OCS envelope converter.
 */
interface CrateBinaryService {
    @Streaming
    @GET("apps/crate/artwork/{itemId}")
    suspend fun getArtwork(
        @Path("itemId") itemId: Long,
        @Query("size") size: String? = null,
    ): ResponseBody

    @Multipart
    @POST("apps/crate/artwork/{itemId}")
    suspend fun uploadArtwork(
        @Path("itemId") itemId: Long,
        @Part file: MultipartBody.Part,
    ): ResponseBody

    @DELETE("apps/crate/artwork/{itemId}")
    suspend fun deleteArtwork(
        @Path("itemId") itemId: Long,
    )

    @Streaming
    @GET("apps/crate/export")
    suspend fun export(
        @Query("format") format: String = "csv",
        @Query("scope") scope: String = "owned",
        @Query("category") category: String = "all",
        @Query("includeEnriched") includeEnriched: Int = 0,
        @Query("includeMarket") includeMarket: Int = 0,
    ): ResponseBody
}
