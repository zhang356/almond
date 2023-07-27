package app.almondally.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST


public interface RelevanceStoreService {
    @POST("trigger_limited/")
    suspend fun getRelevanceStoreResponse(@Body relevanceStoreRequestBody: RelevanceStoreRequestBody): Response<RelevanceStoreResponseBody>
}