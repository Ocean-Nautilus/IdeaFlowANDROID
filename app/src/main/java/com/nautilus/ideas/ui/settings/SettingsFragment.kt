package com.nautilus.ideas.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.appcompat.app.AppCompatDelegate
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.R
import com.nautilus.ideas.data.AppSettings
import com.nautilus.ideas.viewmodels.SettingsViewModel
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate
import kotlinx.coroutines.launch
import com.nautilus.ideas.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::exportTo)
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::importFrom)
    }

    private var _binding: FragmentSettingsBinding? = null

    // Обращаться к binding можно только между onCreateView и onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = (requireActivity().application as IdeasApp).settings
        val themeLabels = resources.getStringArray(R.array.theme_options)
        binding.themeInput.setSimpleItems(themeLabels)
        binding.themeInput.setText(themeLabels[AppSettings.THEME_MODES.indexOf(settings.themeMode)], false)
        binding.themeInput.setOnItemClickListener { _, _, position, _ ->
            settings.themeMode = AppSettings.THEME_MODES[position]
            AppCompatDelegate.setDefaultNightMode(settings.themeMode)
        }
        binding.showDescriptionsSwitch.isChecked = settings.showDescriptions
        binding.showDescriptionsSwitch.setOnCheckedChangeListener { _, checked ->
            settings.showDescriptions = checked
        }
        binding.exportButton.setOnClickListener {
            exportFile.launch("IdeaFlow-${LocalDate.now()}.json")
        }
        binding.importButton.setOnClickListener { importFile.launch(arrayOf("*/*")) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transfer.collect { state ->
                    binding.exportButton.isEnabled = !state.isBusy
                    binding.importButton.isEnabled = !state.isBusy
                    binding.transferProgress.visibility = if (state.isBusy) View.VISIBLE else View.GONE
                    state.message?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Обнуляем binding, иначе фрагмент удержит View и получим утечку памяти.
        _binding = null
    }
}
