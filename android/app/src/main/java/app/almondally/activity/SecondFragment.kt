package app.almondally.activity

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import app.almondally.R
import app.almondally.databinding.FragmentSecondBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        val spinner: Spinner = binding.caregiverRole
// Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            requireActivity(),
            R.array.caregiver_role_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner.adapter = adapter
        }

        binding.finishOnboardingButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                storeOnboardingInfo(
                    binding.patientName.text.toString(),
                    binding.caregiverName.text.toString(),
                    binding.caregiverRole.getSelectedItem().toString()
                )
            }

            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        return binding.root
    }

    suspend fun storeOnboardingInfo(patientName:String, caregiverName: String, caregiverRole: String) {
        var activity:MainActivity = requireActivity() as MainActivity
        activity.getOnboardingDataStore().edit { onboardingInfo ->
            onboardingInfo[stringPreferencesKey(activity.ONBOARDING_DATASTORE_PATIENT_NAME_KEY)] = patientName
            onboardingInfo[stringPreferencesKey(activity.ONBOARDING_DATASTORE_CAREGIVER_NAME_KEY)] = caregiverName
            onboardingInfo[stringPreferencesKey(activity.ONBOARDING_DATASTORE_CAREGIVER_ROLE_KEY)] = caregiverRole
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}