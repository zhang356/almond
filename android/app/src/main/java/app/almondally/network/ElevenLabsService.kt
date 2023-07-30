package app.almondally.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ElevenLabsService {
    @Headers("xi-api-key: 42121fe6e05329406baac97cf70f3bed", "accept: audio/mpeg") // TODO hide api key, davidzmh
    @POST("v1/text-to-speech/EXAVITQu4vr4xnSDxMaL")
    suspend fun getElevenLabsResponse(@Body elevenLabsRequestBody: ElevenLabsRequestBody): Response<ResponseBody>
}