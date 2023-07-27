package app.almondally.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST


public interface RelevanceQueryService {
    @POST("trigger_limited/")
    suspend fun getRelevanceQueryResponse(@Body relevanceQueryRequestBody: RelevanceQueryRequestBody): Response<RelevanceQueryResponseBody>
}