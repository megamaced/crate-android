package com.megamaced.crate.data.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Non-OCS endpoints that accept multipart uploads or deletions of an item's
 * images. These bypass the OCS envelope converter. Reading the images is
 * Coil's job, through the same authenticated client.
 */
interface CrateBinaryService {
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

    @Multipart
    @POST("apps/crate/photo/{itemId}/{slot}")
    suspend fun uploadPhoto(
        @Path("itemId") itemId: Long,
        @Path("slot") slot: Int,
        @Part file: MultipartBody.Part,
    ): ResponseBody

    @DELETE("apps/crate/photo/{itemId}/{slot}")
    suspend fun deletePhoto(
        @Path("itemId") itemId: Long,
        @Path("slot") slot: Int,
    )
}
