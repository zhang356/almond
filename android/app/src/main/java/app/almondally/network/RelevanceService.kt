package app.almondally.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST


public interface RelevanceService {
    @POST("trigger_limited/")
    suspend fun getRelevanceResponse(@Body relevanceRequestBody: RelevanceRequestBody): Response<RelevanceResponseBody>
}