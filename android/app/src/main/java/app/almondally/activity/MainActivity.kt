package app.almondally.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var speechConfig: SpeechConfig? = null
    private var microphoneStream: MicrophoneStream? = null

    private lateinit var retrofitForRelevance: Retrofit
    private lateinit var retrofitForElevenLabs: Retrofit

    private val ONBOARDING_DATASTORE_KEY = "onboarding_info"
    val ONBOARDING_DATASTORE_PATIENT_NAME_KEY = "patient_name"
    val ONBOARDING_DATASTORE_CAREGIVER_NAME_KEY = "caregiver_name"
    val ONBOARDING_DATASTORE_CAREGIVER_ROLE_KEY = "caregiver_role"

    val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = ONBOARDING_DATASTORE_KEY)

    enum class Mode {
        LISTENING, QNA
    }
    private var mode: Mode = Mode.LISTENING

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

    fun getOnboardingDataStore() = onboardingDataStore

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

        lifecycleScope.launch {
            onboardingDataStore.data.first()
            // You should also handle IOExceptions here.
        }
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
            R.id.action_switch_mode -> {
                onSwitchModeButtonTapped(item)
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

    private fun onSwitchModeButtonTapped(item: MenuItem) {
        if (item.title == resources.getString(R.string.listening)) {
            mode = Mode.QNA
            item.title = resources.getString(R.string.qna)
        } else if (item.title == resources.getString(R.string.qna)) {
            mode = Mode.LISTENING
            item.title = resources.getString(R.string.listening)
        }
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
            Log.i(activityTag, "mode: $mode")
            if (finalResult != "" && mode.name == Mode.QNA.name) {
                stopReco()
                askRelevance("", finalResult)
            }
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
        mode = Mode.LISTENING
        Log.i(activityTag, "About to execute Ask Relevance: "+ mode.name)
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

        retrofitForElevenLabs = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(mOkHttpClient)
            .baseUrl(BaseURLs.ELEVEN_LABS)
            .build()

        val elevenLabsService: ElevenLabsService =
            retrofitForElevenLabs.create(ElevenLabsService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            val onboardingData = runBlocking { onboardingDataStore.data.first() }
            var relevanceRequestBody = RelevanceRequestBody(RelevanceRequestBodyParam("", question, onboardingData[stringPreferencesKey(ONBOARDING_DATASTORE_PATIENT_NAME_KEY)]?: "", onboardingData[stringPreferencesKey(ONBOARDING_DATASTORE_CAREGIVER_NAME_KEY)]?: "", onboardingData[stringPreferencesKey(ONBOARDING_DATASTORE_CAREGIVER_ROLE_KEY)]?: ""))
            Log.i(activityTag, relevanceRequestBody.toString())

            val response = relevanceService.getRelevanceResponse(relevanceRequestBody)
            if (response.isSuccessful) {
                val answer = response.body()?.output?.answer
                Log.i(activityTag, answer ?: "empty")
                val elevenLabsRequestBody = ElevenLabsRequestBody(answer)
                Log.i(activityTag, "prepare to convert text to speech: $mode")
                val audioResponse = elevenLabsService.getElevenLabsResponse(elevenLabsRequestBody)
                if (audioResponse.isSuccessful) {
                    Log.i(activityTag, "prepare to respond with audio: $mode")
                    val audioResponseBody = audioResponse.body()?.bytes()
                    if (audioResponseBody != null) {
                        try {
                            val tempFile = File.createTempFile("audio", "temp", cacheDir)
                            tempFile.deleteOnExit()
                            val fos = FileOutputStream(tempFile)
                            fos.write(audioResponseBody)
                            fos.close()

                            val mediaPlayer = MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                start()
                                setOnCompletionListener {
                                    Log.i(activityTag, "playback finished: $mode")
                                    mode = Mode.QNA
                                    startReco()
                                    Log.i(activityTag, "convert mode to Q&A: $mode")
                                }
                            }
                        } catch (e: IOException) {
                            Log.e("Exception", "File write failed: $e")
                        }
                    }
                } else {
                    Log.e(activityTag, audioResponse.errorBody().toString())
                }
//                    val elevenLabsRequestBody = ElevenLabsRequestBody(answer)
//                    Log.d(TAG, Gson().toJson(elevenLabsRequestBody))
//                    binding.answer.append(response.body()?.output?.answer ?: "empty")
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