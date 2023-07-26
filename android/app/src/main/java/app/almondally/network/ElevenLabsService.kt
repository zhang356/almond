package app.almondally.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ElevenLabsService {
    @Headers("xi-api-key: 5abb6166c63ca0f1a217f9774d14ac76", "accept: audio/mpeg") // TODO hide api key
    @POST("v1/text-to-speech/EXAVITQu4vr4xnSDxMaL")
    suspend fun getElevenLabsResponse(@Body elevenLabsRequestBody: ElevenLabsRequestBody): Response<ResponseBody>
}