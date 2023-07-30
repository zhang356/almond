package app.almondally.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import app.almondally.R
import app.almondally.databinding.ActivityMainBinding
import app.almondally.network.BaseURLs
import app.almondally.network.ElevenLabsRequestBody
import app.almondally.network.ElevenLabsService
import app.almondally.network.RelevanceQueryRequestBody
import app.almondally.network.RelevanceQueryRequestBodyParam
import app.almondally.network.RelevanceQueryService
import app.almondally.network.RelevanceStoreRequestBody
import app.almondally.network.RelevanceStoreRequestBodyParam
import app.almondally.network.RelevanceStoreService
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var speechConfig: SpeechConfig? = null
    private var microphoneStream: MicrophoneStream? = null

    private lateinit var retrofitForRelevanceQuery: Retrofit
    private lateinit var retrofitForRelevanceStore: Retrofit
    private lateinit var retrofitForElevenLabs: Retrofit

    private lateinit var latencyPlayer: MediaPlayer
    private lateinit var tempMediaPlayer: MediaPlayer


    private val ONBOARDING_DATASTORE_KEY = "onboarding_info"
    val ONBOARDING_DATASTORE_PATIENT_NAME_KEY = "patient_name"
    val ONBOARDING_DATASTORE_CAREGIVER_NAME_KEY = "caregiver_name"
    val ONBOARDING_DATASTORE_CAREGIVER_ROLE_KEY = "caregiver_role"

    val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = ONBOARDING_DATASTORE_KEY)
    val Context.conversationStore: DataStore<Preferences> by preferencesDataStore(name = "conversation")
    var shortTermMemory: String = ""

    enum class Mode {
        LISTENING, QNA
    }
    private var mode: Mode = Mode.LISTENING


    val mHttpLoggingInterceptor = HttpLoggingInterceptor()
        .setLevel(HttpLoggingInterceptor.Level.BODY)

    val mOkHttpClient = OkHttpClient
        .Builder()
        .addInterceptor(mHttpLoggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val runnableCode: Runnable = object : Runnable {
        override fun run() {
            Log.i(activityTag, "run uploadToLongTermMemory and schedule the next run")
            uploadToLongTermMemory()
            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(30))
        }
    }

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
    fun getConverstaionDataStore() = conversationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your app.
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
            handler.post(runnableCode)
            initReco();
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
        val navHostFragment: NavHostFragment =
        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val currentFragment:FirstFragment = navHostFragment.childFragmentManager.fragments[0] as FirstFragment

        if (item.title == resources.getString(R.string.listening)) {
            mode = Mode.QNA
            item.title = resources.getString(R.string.qna)
            val mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.qna)
            mediaPlayer.setOnCompletionListener {
                startReco()
                it.reset()
                Log.i(activityTag, "before releasing: switch mode audio")
                it.release()
                Log.i(activityTag, "after releasing: switch mode audio")
                currentFragment.updateFace(R.drawable.listening)
            }
            stopReco()
            mediaPlayer.start()

            currentFragment.updateFace(R.drawable.speaking)
        } else if (item.title == resources.getString(R.string.qna)) {
            mode = Mode.LISTENING
            item.title = resources.getString(R.string.listening)
            currentFragment.updateFace(R.drawable.listening)
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
    private fun initReco() {
        speechReco.recognized.addEventListener { sender, e ->
            val navHostFragment: NavHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            val currentFragment:FirstFragment = navHostFragment.childFragmentManager.fragments[0] as FirstFragment
            val finalResult = e.result.text
            Log.i(activityTag, finalResult)
            Log.i(activityTag, "mode: $mode")
            if (finalResult != "") {
                storeConversation("Human", finalResult)
                if (mode.name == Mode.QNA.name) {
                    stopReco()
                    askRelevance(shortTermMemory, finalResult)
                    latencyPlayer = MediaPlayer.create(getApplicationContext(), getLatencySource())
                    latencyPlayer.setOnCompletionListener {
                        it.reset()
                        Log.i(activityTag, "before releasing: latency audio")
                        it.release()
                        Log.i(activityTag, "after releasing: latency audio")
                         currentFragment.updateFace(R.drawable.thinking)
                    }
                    val latencyPlaybackRunnable: Runnable = object : Runnable {
                        override fun run() {
                            latencyPlayer.start()
                             currentFragment.updateFace(R.drawable.speaking)
                        }
                    }
                    handler.postDelayed(latencyPlaybackRunnable, TimeUnit.SECONDS.toMillis(1))
                }
            }
        }
    }

    private fun getLatencySource(): Int {
        val list = listOf(R.raw.latency1, R.raw.latency2, R.raw.latency3, R.raw.latency4)
        return list.random()
    }
    fun startReco() {
        val task = speechReco.startContinuousRecognitionAsync()
        executorService.submit {
            task.get()
            Log.i(activityTag, "Continuous recognition finished. Stopping speechReco")
        }
    }

    fun stopReco() {
        Log.i(activityTag, "stopReco()")
        speechReco.stopContinuousRecognitionAsync()
    }

    private fun destroyMicrophoneStream() {
        if (microphoneStream != null) {
            microphoneStream?.close()
            microphoneStream = null
        }
    }

    private fun storeRelevance(key: String, conversation: String) {
        Log.i(activityTag, "About to execute Store Relevance: "+ mode.name)

        retrofitForRelevanceStore = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(mOkHttpClient)
            .baseUrl(BaseURLs.RELEVANCE_STORE)
            .build()
        val relevanceStoreService: RelevanceStoreService =
            retrofitForRelevanceStore.create(RelevanceStoreService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            Log.i(activityTag, "store relevance call thread: ${Thread.currentThread().name}")
            val relevanceStoreRequestBody =
                RelevanceStoreRequestBody(RelevanceStoreRequestBodyParam(conversation, key))
            Log.i(activityTag, relevanceStoreRequestBody.toString())
            val response =
                relevanceStoreService.getRelevanceStoreResponse(relevanceStoreRequestBody)
            if (response.isSuccessful) {
                Log.i(activityTag, response.body()?.output?.inserted ?: "null response")
            } else {
                Log.e(activityTag, response.errorBody().toString())
            }
        }
    }

    private fun askRelevance(shortTermMemoryRecentConversation: String, question: String) {
        mode = Mode.LISTENING
        Log.i(activityTag, "About to execute Ask Relevance: "+ mode.name)

        retrofitForRelevanceQuery = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(mOkHttpClient)
            .baseUrl(BaseURLs.RELEVANCE_QUERY)
            .build()
        val relevanceQueryService: RelevanceQueryService =
            retrofitForRelevanceQuery.create(RelevanceQueryService::class.java)

        retrofitForElevenLabs = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .client(mOkHttpClient)
            .baseUrl(BaseURLs.ELEVEN_LABS)
            .build()

        val elevenLabsService: ElevenLabsService =
            retrofitForElevenLabs.create(ElevenLabsService::class.java)

        CoroutineScope(Dispatchers.IO).launch {
            Log.i(activityTag, "thread call askRelevance: ${Thread.currentThread().name}")
            val onboardingData =  onboardingDataStore.data.first()
            val relevanceQueryRequestBody = RelevanceQueryRequestBody(RelevanceQueryRequestBodyParam(
                shortTermMemoryRecentConversation ,
                question,
                onboardingData[stringPreferencesKey(ONBOARDING_DATASTORE_PATIENT_NAME_KEY)]?: "",
                onboardingData[stringPreferencesKey(ONBOARDING_DATASTORE_CAREGIVER_NAME_KEY)]?: "",
                onboardingData[stringPreferencesKey(ONBOARDING_DATASTORE_CAREGIVER_ROLE_KEY)]?: ""))
            Log.i(activityTag, relevanceQueryRequestBody.toString())

            val response = relevanceQueryService.getRelevanceQueryResponse(relevanceQueryRequestBody)
            if (response.isSuccessful) {
                var answer = response.body()?.output?.answer;
                if (answer == null) {
                    answer = "looks like my memory is empty on this, I can't answer you this question"
                }
                Log.i(activityTag, "Relevance respond with $answer")
                val elevenLabsRequestBody = ElevenLabsRequestBody(answer)
                storeConversation("Almond", answer)
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
                            Log.i(activityTag, "before MediaPlayer().apply")
                            tempMediaPlayer = MediaPlayer()
                            tempMediaPlayer.setDataSource(tempFile.absolutePath)
                            tempMediaPlayer.setOnCompletionListener {
                                Log.i(activityTag, "playback finished: $mode")
                                mode = Mode.QNA
                                startReco()
                                Log.i(activityTag, "convert mode to Q&A: $mode")
                                it.reset()
                                Log.i(activityTag, "before releasing: 11labs readings")
                                it.release()
                                Log.i(activityTag, "after releasing: 11labs readings")
                                CoroutineScope(Dispatchers.Main).launch {
                                    val navHostFragment: NavHostFragment =
                                        supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                                    val currentFragment: FirstFragment =
                                        navHostFragment.childFragmentManager.fragments[0] as FirstFragment
                                    currentFragment.updateFace(R.drawable.listening)
                                }
                            }
                            CoroutineScope(Dispatchers.Main).launch {
                                val navHostFragment: NavHostFragment =
                                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                                val currentFragment: FirstFragment =
                                    navHostFragment.childFragmentManager.fragments[0] as FirstFragment
                                currentFragment.updateFace(R.drawable.speaking)
                            }
                            tempMediaPlayer.prepare()
                            tempMediaPlayer.start()
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

    private fun storeConversation(speaker: String, sentence: String) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(activityTag, "thread storeConversation local db: ${Thread.currentThread().name}")
            getConverstaionDataStore().edit { conversation ->
                conversation[stringPreferencesKey((System.currentTimeMillis()).toString())] = "$speaker said \"$sentence\""
            }
        }
    }

    // this function should be updated to clear database in the future
    fun updateKey() {
        CoroutineScope(Dispatchers.IO).launch {
            val allEntries: Map<String, Any> = conversationStore.data
                .catch { e ->
                    if (e is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }
                .map { preferences ->
                    preferences.asMap().mapKeys { it.key.name }
                }
                .first()

            conversationStore.edit { preferences ->
                allEntries.forEach { (key, value) ->
                    // Create the old and new preference keys
                    val oldKey = stringPreferencesKey(key)
                    val newKey = stringPreferencesKey((key.toLong() * 1000).toString())

                    // Remove the old key
                    preferences.remove(oldKey)

                    // Add the value under the new key
                    preferences[newKey] = value.toString()
                }
            }

            val updatedEntries = conversationStore.data
                .catch { e ->
                    if (e is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }
                .map { preferences ->
                    preferences.asMap().mapKeys { it.key.name }
                }
                .first()
            uploadToLongTermMemory()
        }
    }

    private fun uploadToLongTermMemory() {
        val chunkDiff = 60 * 1000
        val shortTermMemoryPeriod = 300 * 1000

        CoroutineScope(Dispatchers.IO).launch {
            Log.i(activityTag, "thread to update longTermMemory: ${Thread.currentThread().name}")
            Log.i(activityTag, "start printing conversation datastore")
            var deleteTimestamp = System.currentTimeMillis()
            val allEntries: Map<Long, String> = conversationStore.data
                .map { preferences ->
                    preferences.asMap().mapKeys { it.key.name.toLong() }.mapValues { it.value.toString() }
                }
                .first()
            val sortedMap: SortedMap<Long, String> = TreeMap(allEntries)

            // Initialize accumulator and previous key
            var accumulator = ""
            var previousKey: Long? = null
            var startTimestamp: Long? = null

            val groupedEntries = TreeMap<Long, Pair<Long, String>>() // Begin timestamp to end timestamp and value

            // Iterate over entries in sorted order
            for ((key, value) in sortedMap) {
                if (previousKey != null && key - previousKey < chunkDiff) {
                    // If the current key is within 15 seconds of the previous key,
                    // concatenate the value to the accumulator
                    accumulator += "$value\n"
                } else {
                    // If the current key is more than 15 seconds away from the previous key,
                    // store the accumulator in the grouped entries map and create a new accumulator
                    if (previousKey != null && startTimestamp != null) {
                        groupedEntries[startTimestamp] = Pair(previousKey, accumulator)
                    }
                    accumulator = "$value\n"
                    startTimestamp = key
                }

                previousKey = key
            }
            // Store the last accumulator
            if (previousKey != null && startTimestamp != null) {
                groupedEntries[startTimestamp] = Pair(previousKey, accumulator)
            }

            // Process the grouped entries
            for ((start, pair) in groupedEntries) {
                val end = pair.first
                val value = pair.second

                if (System.currentTimeMillis() - end < shortTermMemoryPeriod) {
                    Log.i(activityTag, "found short term memory with everything beyond timestamp: $start")
                    deleteTimestamp = start // update deleteTimestamp to a smaller value
                    shortTermMemory = groupedEntries.lastEntry()?.value?.second ?: ""
                    Log.i(activityTag, "current short term Memory is: $shortTermMemory")
                    break
                }
                storeRelevance("conversation-begin-$start", value)
                Log.i(activityTag, "Start: $start, End: $end, Value: $value")
            }
            // Delete keys before the marked timestamp
            conversationStore.edit { preferences ->
                preferences.asMap().keys
                    .filter { it.name.toLong() < deleteTimestamp }
                    .forEach { preferences.remove(it) }
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