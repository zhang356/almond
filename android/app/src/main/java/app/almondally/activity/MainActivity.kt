package app.almondally.activity

import android.Manifest
import android.R.attr.data
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import app.almondally.R
import app.almondally.databinding.ActivityMainBinding
import app.almondally.network.BaseURLs
import app.almondally.network.ElevenLabsRequestBody
import app.almondally.network.ElevenLabsService
import app.almondally.network.RelevanceRequestBody
import app.almondally.network.RelevanceRequestBodyParam
import app.almondally.network.RelevanceService
import com.google.gson.Gson
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var speechConfig: SpeechConfig? = null
    private var microphoneStream: MicrophoneStream? = null

    private lateinit var retrofitForRelevance: Retrofit
    private lateinit var retrofitForElevenLabs: Retrofit

    private val speechReco: SpeechRecognizer by lazy {
        speechConfig = SpeechConfig.fromSubscription(speechSubscriptionKey, speechRegion)
        destroyMicrophoneStream() // in case it was previously initialized
        microphoneStream = MicrophoneStream()

        SpeechRecognizer(
            speechConfig,
            AudioConfig.fromStreamInput(MicrophoneStream.create())
        )
    }

    val TAG = "DEBUG"

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        // Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher. You can use either a val, as shown in this snippet,
// or a lateinit var in your onAttach() or onCreate() method.
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    Log.v("DEBUG", "record audio permission granted")

                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    Log.v("DEBUG", "record audio permission denied")
                }
            }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                Log.v(TAG, "record audio permission granted")
            }
            //shouldShowRequestPermissionRationale(...) -> {
            // In an educational UI, explain to the user why your app requires this
            // permission for a specific feature to behave as expected, and what
            // features are disabled if it's declined. In this UI, include a
            // "cancel" or "no thanks" button that lets the user continue
            // using your app without granting the permission.
            //showInContextUI(...)

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO)
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)



    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_start_stop -> {
                onStartStopButtonTapped(item)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()

    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        val elevenLabsRequestBody = ElevenLabsRequestBody("Hello, I'm Jack")

        val mHttpLoggingInterceptor = HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)

        val mOkHttpClient = OkHttpClient
            .Builder()
            .addInterceptor(mHttpLoggingInterceptor)
            .build()

        retrofitForElevenLabs = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(mOkHttpClient)
            .baseUrl(BaseURLs.ELEVEN_LABS)
            .build()

        val elevenLabsService: ElevenLabsService =
            retrofitForElevenLabs.create(ElevenLabsService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            val response = elevenLabsService.getElevenLabsResponse(elevenLabsRequestBody)
            if (response.isSuccessful) {
                CoroutineScope(Dispatchers.Main).launch {
                    val audioResponseBody = response.body()?.bytes()
                    if (audioResponseBody != null) {
                        try {
                            val outputStreamWriter = OutputStreamWriter(
                                context.openFileOutput(
                                    "sampleAudio",
                                    MODE_PRIVATE
                                )
                            )
                            outputStreamWriter.write(data)
                            outputStreamWriter.close()
                        } catch (e: IOException) {
                            Log.e("Exception", "File write failed: $e")
                        }
                    }
                }
            } else {
                Log.e(activityTag, response.errorBody().toString())
            }
        }
        return super.onCreateView(name, context, attrs)
    }

    private fun onStartStopButtonTapped(item: MenuItem) {
        if (item.title == resources.getString(R.string.start)) {
            startReco()
            item.title = resources.getString(R.string.stop)
        } else if (item.title == resources.getString(R.string.stop)) {
            stopReco()
            item.title = resources.getString(R.string.start)
        }
    }

    private fun startReco() {
        speechReco.recognized.addEventListener { sender, e ->
            val finalResult = e.result.text
            Log.i(activityTag, finalResult)
            if (finalResult != "") {
                askRelevance("", finalResult)
            } else {
                Log.i(activityTag, "avoid sending empty question")
            }
//            stopReco()
        }

        val task = speechReco.startContinuousRecognitionAsync()
        executorService.submit {
            task.get()
            Log.i(activityTag, "Continuous recognition finished. Stopping speechReco")
        }
    }

    private fun stopReco() {
        speechReco.stopContinuousRecognitionAsync()
    }

    private fun destroyMicrophoneStream() {
        if (microphoneStream != null) {
            microphoneStream?.close()
            microphoneStream = null
        }
    }

    private fun askRelevance(context: String, question: String) {
        val mHttpLoggingInterceptor = HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)

        val mOkHttpClient = OkHttpClient
            .Builder()
            .addInterceptor(mHttpLoggingInterceptor)
            .build()

        retrofitForRelevance = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(mOkHttpClient)
            .baseUrl(BaseURLs.RELEVANCE)
            .build()
        val relevanceService: RelevanceService =
            retrofitForRelevance.create(RelevanceService::class.java)

        val relevanceRequestBody = RelevanceRequestBody(RelevanceRequestBodyParam("", question))
        Log.d(activityTag, Gson().toJson(relevanceRequestBody))

        CoroutineScope(Dispatchers.IO).launch {
            val response = relevanceService.getRelevanceResponse(relevanceRequestBody)
            if (response.isSuccessful) {
                CoroutineScope(Dispatchers.Main).launch {
                    val answer = response.body()?.output?.answer
                    Log.i(activityTag, answer ?: "empty")
//                    val elevenLabsRequestBody = ElevenLabsRequestBody(answer)
//                    Log.d(TAG, Gson().toJson(elevenLabsRequestBody))
//                    binding.answer.append(response.body()?.output?.answer ?: "empty")
                }
            } else {
                Log.e(activityTag, response.errorBody().toString())
            }
        }
    }

    companion object {
        private const val speechSubscriptionKey = "7404f9e42967403e8f63d646646c8195"
        private const val speechRegion = "eastus"
        private const val activityTag = "MainActivity"
        private val executorService = Executors.newCachedThreadPool()
    }

}