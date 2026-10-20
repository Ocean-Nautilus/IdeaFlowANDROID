package com.nautilus.ideas.ui.details

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nautilus.ideas.R
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.databinding.FragmentIdeaDetailsBinding
import com.nautilus.ideas.ui.editor.IdeaEditorFragment
import com.nautilus.ideas.utils.DateFormatter
import com.nautilus.ideas.utils.IdeaStatusUi
import com.nautilus.ideas.viewmodels.DetailsEvent
import com.nautilus.ideas.viewmodels.DetailsUiState
import com.nautilus.ideas.viewmodels.IdeaDetailsViewModel
import kotlinx.coroutines.launch

/**
 * Экран детального просмотра идеи.
 *
 * Показывает всё, что не помещается в карточку списка: полный текст,
 * даты создания и изменения. Отсюда идею правят и удаляют.
 */
class IdeaDetailsFragment : Fragment() {

    private var _binding: FragmentIdeaDetailsBinding? = null
    private val binding get() = _binding!!

    private val ideaId: Long
        get() = requireArguments().getLong(ARG_IDEA_ID)

    private val viewModel: IdeaDetailsViewModel by viewModels {
        IdeaDetailsViewModel.factory(ideaId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdeaDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpButtons()
        observeState()
        observeEvents()
    }

    private fun setUpButtons() {
        binding.closeButton.setOnClickListener { findNavController().navigateUp() }

        binding.editButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_details_to_editor,
                bundleOf(IdeaEditorFragment.ARG_IDEA_ID to ideaId)
            )
        }

        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    /**
     * Диалог подтверждения удаления - требование ТЗ.
     *
     * Удаление необратимо, поэтому одного нажатия мало: спрашиваем
     * отдельно и называем идею по имени, чтобы было видно, что удаляем.
     */
    private fun confirmDelete() {
        val title = viewModel.uiState.value.idea?.idea?.title ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_dialog_title)
            .setMessage(getString(R.string.delete_dialog_message, title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: DetailsUiState) = with(binding) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        content.visibility = if (state.idea == null) View.GONE else View.VISIBLE

        // Идеи нет и мы её не удаляли - значит, она пропала из базы
        // (например, вместе со своей категорией). Возвращаемся к списку.
        if (!state.isLoading && state.idea == null && !state.isDeleting) {
            findNavController().navigateUp()
            return@with
        }

        val item = state.idea ?: return@with
        bindIdea(item)

        deleteButton.isEnabled = !state.isDeleting
        editButton.isEnabled = !state.isDeleting
    }

    private fun bindIdea(item: IdeaWithDetails) = with(binding) {
        titleText.text = item.idea.title

        contentText.text = item.idea.content
        contentText.visibility = if (item.idea.content.isBlank()) View.GONE else View.VISIBLE
        emptyContentText.visibility =
            if (item.idea.content.isBlank()) View.VISIBLE else View.GONE

        categoryText.text = item.category.name
        val categoryColor = runCatching { Color.parseColor(item.category.colorHex) }
            .getOrDefault(Color.GRAY)
        categoryDot.backgroundTintList = ColorStateList.valueOf(categoryColor)

        statusText.setText(IdeaStatusUi.labelOf(item.idea.status))
        statusText.setTextColor(
            ContextCompat.getColor(requireContext(), IdeaStatusUi.colorOf(item.idea.status))
        )

        ratingText.text = getString(R.string.details_rating_value, item.idea.rating)

        createdText.text = getString(
            R.string.details_created,
            DateFormatter.formatFull(item.idea.createdAt)
        )
        updatedText.text = getString(
            R.string.details_updated,
            DateFormatter.formatFull(item.idea.updatedAt)
        )
        // Если идею ни разу не правили, вторая дата совпадает с первой
        // и показывать её незачем.
        updatedText.visibility =
            if (item.idea.updatedAt > item.idea.createdAt) View.VISIBLE else View.GONE

    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DetailsEvent.Deleted -> findNavController().navigateUp()

                        is DetailsEvent.DeleteFailed -> Snackbar.make(
                            binding.root,
                            getString(R.string.error_delete_failed, event.reason.orEmpty()),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_IDEA_ID = "ideaId"
    }
}
