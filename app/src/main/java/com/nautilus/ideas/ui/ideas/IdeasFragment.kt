package com.nautilus.ideas.ui.ideas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.nautilus.ideas.R
import com.nautilus.ideas.ui.details.IdeaDetailsFragment
import com.nautilus.ideas.databinding.FragmentIdeasBinding
import com.nautilus.ideas.ui.adapters.IdeaAdapter
import com.nautilus.ideas.viewmodels.IdeasUiState
import com.nautilus.ideas.viewmodels.IdeasViewModel
import kotlinx.coroutines.launch

/**
 * Главный экран: список всех идей пользователя.
 *
 * Фрагмент не обращается к базе и ничего не вычисляет — он только
 * показывает то, что прислала ViewModel, и передаёт ей нажатия.
 */
class IdeasFragment : Fragment() {

    private var _binding: FragmentIdeasBinding? = null

    // Обращаться к binding можно только между onCreateView и onDestroyView.
    private val binding get() = _binding!!

    // by viewModels: объект создаётся один раз и переживает поворот экрана.
    private val viewModel: IdeasViewModel by viewModels { IdeasViewModel.Factory }

    private lateinit var adapter: IdeaAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdeasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpList()
        setUpButtons()
        observeUiState()
    }

    private fun setUpList() {
        adapter = IdeaAdapter(onIdeaClick = { item ->
            findNavController().navigate(
                R.id.action_ideas_to_details,
                bundleOf(IdeaDetailsFragment.ARG_IDEA_ID to item.idea.id)
            )
        })

        binding.ideasList.layoutManager = LinearLayoutManager(requireContext())
        binding.ideasList.adapter = adapter

        // Размер карточки не зависит от содержимого списка, поэтому
        // RecyclerView может не пересчитывать свои размеры на каждой вставке.
        binding.ideasList.setHasFixedSize(true)
    }

    private fun setUpButtons() {
        binding.addIdeaButton.setOnClickListener {
            // Без аргумента форма открывается пустой - создаём новую идею.
            findNavController().navigate(R.id.action_ideas_to_editor)
        }
    }

    /**
     * Подписка на состояние экрана.
     *
     * repeatOnLifecycle останавливает сбор данных, когда экран уходит
     * в фон, и возобновляет при возврате. Без этого фрагмент продолжал бы
     * получать обновления невидимым и зря тратить батарею.
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: IdeasUiState) = with(binding) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        emptyState.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        ideasList.visibility = if (state.ideas.isEmpty()) View.GONE else View.VISIBLE

        subtitleText.text = if (state.isLoading) {
            getString(R.string.ideas_loading)
        } else {
            // Правильное окончание: 1 идея, 2 идеи, 5 идей.
            resources.getQuantityString(
                R.plurals.ideas_count,
                state.ideas.size,
                state.ideas.size
            )
        }

        adapter.submitList(state.ideas)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Адаптер отвязываем явно: RecyclerView держит на него ссылку,
        // а тот — на ViewHolder-ы с View уничтоженного экрана.
        binding.ideasList.adapter = null
        _binding = null
    }
}
