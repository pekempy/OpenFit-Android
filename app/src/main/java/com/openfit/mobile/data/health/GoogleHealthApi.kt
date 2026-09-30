package com.openfit.mobile.data.health

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/** Raw Google Health API v4 surface - same endpoints OpenFit desktop calls
 * (electron/google-health-service.cjs). Responses are kept as generic
 * [JsonObject] rather than fully-typed DTOs: the schema is deeply nested and
 * varies per data type, and HealthTranslator.kt navigates it the same
 * defensive, optional-chaining way the reference implementation does. */
interface GoogleHealthApi {

    @GET("users/me/dataTypes/{type}/dataPoints:reconcile")
    suspend fun listDataPointsReconcile(
        @Header("Authorization") bearer: String,
        @Path("type") type: String,
        @Query("filter") filter: String,
        @Query("pageSize") pageSize: String,
        @Query("dataSourceFamily") dataSourceFamily: String? = null,
        @Query("pageToken") pageToken: String? = null,
    ): JsonObject

    @GET("users/me/dataTypes/{type}/dataPoints")
    suspend fun listDataPoints(
        @Header("Authorization") bearer: String,
        @Path("type") type: String,
        @Query("filter") filter: String,
        @Query("pageSize") pageSize: String,
        @Query("pageToken") pageToken: String? = null,
    ): JsonObject

    @POST("users/me/dataTypes/{type}/dataPoints:dailyRollUp")
    suspend fun dailyRollUp(
        @Header("Authorization") bearer: String,
        @Path("type") type: String,
        @Body body: JsonObject,
    ): JsonObject

    @GET("users/me/profile")
    suspend fun profile(@Header("Authorization") bearer: String): JsonObject

    @GET("users/me/settings")
    suspend fun settings(@Header("Authorization") bearer: String): JsonObject

    @GET("users/me/pairedDevices")
    suspend fun pairedDevices(
        @Header("Authorization") bearer: String,
        @Query("pageSize") pageSize: String = "100",
    ): JsonObject

    @GET
    suspend fun userInfo(
        @Header("Authorization") bearer: String,
        @Url url: String = "https://www.googleapis.com/oauth2/v3/userinfo",
    ): JsonObject

    companion object {
        const val BASE_URL = "https://health.googleapis.com/v4/"
    }
}
