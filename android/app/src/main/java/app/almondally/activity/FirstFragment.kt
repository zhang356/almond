package app.almondally.activity

import android.content.Intent
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import app.almondally.R
import app.almondally.databinding.FragmentFirstBinding
import app.almondally.network.BaseURLs
import app.almondally.network.ElevenLabsService
import app.almondally.network.RelevanceQueryService
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    val TAG = "DEBUG"

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var speechRecognitionListener: RecognitionListener

    private lateinit var mediaPlayer: MediaPlayer

    private lateinit var retrofitForRelevanceQuery: Retrofit
    private lateinit var retrofitForElevenLabs: Retrofit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context: Context = requireContext()

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE, /*Locale.getDefault()*/
            "en-US"
        )
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_ENABLE_FORMATTING,
            RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
        )

        val mediaPlayer = MediaPlayer.create(context, R.raw.onboarding)
        mediaPlayer.setOnCompletionListener {
            // (activity as MainActivity).startReco() // this will automatically start listening
            it.reset()
            it.release()
            updateFace(R.drawable.listening)
        }
        // (activity as MainActivity).stopReco()
        // mediaPlayer.start()

        val handler = Handler(Looper.getMainLooper())
        val runnableCode: Runnable = object : Runnable {
            override fun run() {
                mediaPlayer.start()
                updateFace(R.drawable.speaking)
            }
        }
        handler.postDelayed(runnableCode, TimeUnit.SECONDS.toMillis(1))

        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator() //add this
        fadeIn.duration = 1000

        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator() //and this
        fadeOut.startOffset = 1000
        fadeOut.duration = 1000

        val mHttpLoggingInterceptor = HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BODY)

        val mOkHttpClient = OkHttpClient
            .Builder()
            .addInterceptor(mHttpLoggingInterceptor)
            .build()

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

        speechRecognitionListener = object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val voiceResults =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (voiceResults == null) {
//                    Log.e(TAG, "No voice results")
                } else {
//                    Log.d(TAG, "Printing matches: ")
                    for (match in voiceResults) {
                        Log.d(TAG, match!!)
                    }
                    val bestMatch = voiceResults[0]

//                    var relevanceRequestBody = RelevanceQueryRequestBody(RelevanceQueryRequestBodyParam("", bestMatch))
//                    Log.d(TAG, Gson().toJson(relevanceRequestBody))
//
//                    var elevenLabsRequestBody = ElevenLabsRequestBody(bestMatch)
//                    Log.d(TAG, Gson().toJson(elevenLabsRequestBody))

                    // TODO call relevance.ai then append answer to binding.answer and play audio
//                    CoroutineScope(Dispatchers.IO).launch {
//
//                        val response = relevanceService.getRelevanceResponse(relevanceRequestBody)
//                        if (response.isSuccessful) {
//                            CoroutineScope(Dispatchers.Main).launch {
//                                binding.answer.append(response.body()?.output?.answer ?: "empty")
//                            }
//
//                        } else {
//                            Log.e(TAG, response.errorBody().toString())
//                        }
//
//                    }
/*
                    // then display recognized text
//                    binding.question.visibility = View.INVISIBLE
                    binding.question.startAnimation(fadeOut)
                    binding.question.text = bestMatch
                    binding.question.visibility = View.VISIBLE
                    binding.question.startAnimation(fadeIn)

                    // then play let-me-think placeholder, and display text
//                    binding.answer.visibility = View.INVISIBLE
                    binding.answer.startAnimation(fadeOut)
                    binding.answer.text = "Hey Phillip, let me think. "
                    binding.answer.visibility = View.VISIBLE
                    binding.answer.startAnimation(fadeIn)
*/
                    mediaPlayer.start() // no need to call prepare(); create() does that for you
                }

                // then start listening again? Or switch to recording mode?
//                speechRecognizer.startListening(speechRecognizerIntent)
//                Log.d(TAG, "Speech restarted")
            }

            override fun onReadyForSpeech(params: Bundle) {
//                Log.d(TAG, "Ready for speech")
            }

            override fun onError(error: Int) {
//                Log.e(TAG, "Error listening for speech: $error")
            }

            override fun onBeginningOfSpeech() {
//                Log.d(TAG, "Speech starting")
            }

            override fun onBufferReceived(buffer: ByteArray) {
                // no-op
            }

            override fun onEndOfSpeech() {
//                Log.d(TAG, "Speech ends")
            }

            override fun onEvent(eventType: Int, params: Bundle) {
                // no-op
            }

            override fun onPartialResults(partialResults: Bundle) {
                // no-op
            }

            override fun onRmsChanged(rmsdB: Float) {
                // no-op
            }
        }

        binding.fabAsk.setOnClickListener { view ->
            onAskButtonTapped(view)
        }
    }

    fun updateFace(drawable: Int) {
        Glide.with(requireContext())
            .load(drawable)
            .into(binding.face)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun onAskButtonTapped(view: View) {
        // switch to answer mode
        Snackbar.make(view, "Ask button tapped", Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.fab_ask)
            .show()

        // TODO stop recording?

        //start recognizing user
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        speechRecognizer.setRecognitionListener(speechRecognitionListener)
        speechRecognizer.startListening(speechRecognizerIntent)

    }

}