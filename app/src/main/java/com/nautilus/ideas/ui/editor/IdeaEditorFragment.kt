package com.nautilus.ideas.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.nautilus.ideas.R
import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.databinding.FragmentIdeaEditorBinding
import com.nautilus.ideas.utils.IdeaStatusUi
import com.nautilus.ideas.viewmodels.EditorEvent
import com.nautilus.ideas.viewmodels.EditorUiState
import com.nautilus.ideas.viewmodels.IdeaEditorViewModel
import kotlinx.coroutines.launch

/**
 * Экран создания и редактирования идеи.
 *
 * Один экран на оба случая: если в аргументах пришёл id идеи, форма
 * заполняется её данными, иначе открывается пустой. Разделять это
 * на два почти одинаковых фрагмента не имеет смысла.
 */
class IdeaEditorFragment : Fragment() {

    private var _binding: FragmentIdeaEditorBinding? = null
    private val binding get() = _binding!!

    private val ideaId: Long
        get() = arguments?.getLong(ARG_IDEA_ID) ?: IdeaEditorViewModel.NEW_IDEA_ID

    private val viewModel: IdeaEditorViewModel by viewModels {
        IdeaEditorViewModel.factory(ideaId)
    }

    /**
     * Пока форма заполняется данными из базы, слушатели полей должны
     * молчать: иначе программная установка текста будет воспринята
     * как ввод пользователя и затрёт состояние.
     */
    private var isBinding = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdeaEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpStatusChips()
        setUpInputs()
        setUpButtons()
        observeState()
        observeEvents()
    }

    private fun setUpStatusChips() {
        // Чипы создаются кодом, а не в разметке: список статусов задан
        // перечислением, и при добавлении нового статуса разметку
        // править не придётся.
        IdeaStatus.entries.forEach { status ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(IdeaStatusUi.labelOf(status))
                isCheckable = true
                tag = status
            }
            binding.statusChips.addView(chip)
        }

        binding.statusChips.setOnCheckedStateChangeListener { group, checkedIds ->
            if (isBinding) return@setOnCheckedStateChangeListener
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            (chip?.tag as? IdeaStatus)?.let(viewModel::onStatusSelected)
        }
    }

    private fun setUpInputs() {
        binding.titleInput.doAfterTextChanged { text ->
            if (isBinding) return@doAfterTextChanged
            viewModel.onTitleChanged(text?.toString().orEmpty())
            // Ошибка снимается сразу, как только пользователь начал печатать.
            binding.titleLayout.error = null
        }

        binding.contentInput.doAfterTextChanged { text ->
            if (isBinding) return@doAfterTextChanged
            viewModel.onContentChanged(text?.toString().orEmpty())
        }

        binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onCategorySelected(position)
        }

        binding.ratingSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.onRatingChanged(value.toInt())
            binding.ratingValue.text = value.toInt().toString()
        }
    }

    private fun setUpButtons() {
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.closeButton.setOnClickListener { findNavController().navigateUp() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: EditorUiState) = with(binding) {
        headerText.setText(
            if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit
        )
        saveButton.isEnabled = !state.isSaving
        savingProgress.visibility = if (state.isSaving) View.VISIBLE else View.GONE

        if (state.isLoading) return@with

        isBinding = true

        // setText только если значение реально отличается, иначе курсор
        // будет прыгать в начало строки на каждом нажатии клавиши.
        if (titleInput.text?.toString() != state.title) {
            titleInput.setText(state.title)
        }
        if (contentInput.text?.toString() != state.content) {
            contentInput.setText(state.content)
        }

        val categoryNames = state.categories.map { it.name }
        categoryInput.setSimpleItems(categoryNames.toTypedArray())
        categoryNames.getOrNull(state.selectedCategoryIndex)?.let { name ->
            // false - не фильтровать список по введённому тексту,
            // иначе в выпадающем списке останется один пункт.
            categoryInput.setText(name, false)
        }

        for (index in 0 until statusChips.childCount) {
            val chip = statusChips.getChildAt(index) as Chip
            chip.isChecked = chip.tag == state.status
        }

        ratingSlider.value = state.rating.toFloat()
        ratingValue.text = state.rating.toString()

        isBinding = false
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        EditorEvent.Saved -> findNavController().navigateUp()

                        EditorEvent.EmptyTitle -> {
                            // Требование ТЗ: подсветка поля и сообщение.
                            binding.titleLayout.error = getString(R.string.error_title_required)
                            binding.titleInput.requestFocus()
                            showMessage(getString(R.string.error_title_required))
                        }

                        is EditorEvent.SaveFailed -> showMessage(
                            getString(R.string.error_save_failed, event.reason.orEmpty())
                        )
                    }
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_IDEA_ID = "ideaId"
    }
}
