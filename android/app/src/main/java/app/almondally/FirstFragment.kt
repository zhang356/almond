package app.almondally

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import app.almondally.databinding.FragmentFirstBinding
import com.google.android.material.snackbar.Snackbar

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    val TAG = "DEBUG"

    private lateinit var speechRecognizer : SpeechRecognizer
    private lateinit var speechRecognizerIntent : Intent
    private lateinit var speechRecognitionListener: RecognitionListener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        binding.buttonFirst.setOnClickListener {
//            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
//        }

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, /*Locale.getDefault()*/"en-US")
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)

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
                    // TODO call relevance.ai

                    // then display recognized text
                    binding.question.text = bestMatch
                }

                // then start listening again? Or switch to recording mode?
//                speechRecognizer.startListening(speechRecognizerIntent)
//                Log.d(TAG, "Speech restarted")
            }

            override fun onReadyForSpeech(params: Bundle) {
//                Log.d(TAG, "Ready for speech")
            }

            override fun onError(error: Int) {
//                Log.d(TAG, "Error listening for speech: $error")
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