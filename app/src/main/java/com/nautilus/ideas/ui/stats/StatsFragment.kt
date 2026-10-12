package com.nautilus.ideas.ui.stats

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.nautilus.ideas.R
import com.nautilus.ideas.data.model.CategoryCount
import com.nautilus.ideas.data.model.StatusCount
import com.nautilus.ideas.databinding.FragmentStatsBinding
import com.nautilus.ideas.utils.IdeaStatusUi
import com.nautilus.ideas.viewmodels.StatsUiState
import com.nautilus.ideas.viewmodels.StatsViewModel
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels { StatsViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpTiles()
        setUpCharts()
        observeState()
    }

    private fun setUpTiles() {
        // Заготовка под 4 плитки: "Всего", "Новые", "В работе", "Завершено".
        // Сами значения проставляются в render(), здесь только создаём View.
    }

    private fun setUpCharts() = with(binding) {
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(true)
        pieChart.setDrawEntryLabels(false)
        pieChart.legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.legend.orientation = Legend.LegendOrientation.HORIZONTAL

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        barChart.xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        barChart.xAxis.granularity = 1f
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: StatsUiState) = with(binding) {
        emptyStatsText.visibility = if (state.isEmpty) View.VISIBLE else View.GONE

        if (state.isLoading) return@with

        renderPie(state.categoryCounts)
        renderBar(state.statusCounts)
    }

    private fun renderPie(counts: List<CategoryCount>) = with(binding.pieChart) {
        val nonEmpty = counts.filter { it.amount > 0 }
        val entries = nonEmpty.map { PieEntry(it.amount.toFloat(), it.categoryName) }
        val colors = nonEmpty.map { runCatching { Color.parseColor(it.colorHex) }.getOrDefault(Color.GRAY) }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            sliceSpace = 2f
        }
        data = PieData(dataSet)
        invalidate()
    }

    private fun renderBar(counts: List<StatusCount>) = with(binding.barChart) {
        val entries = counts.mapIndexed { index, c -> BarEntry(index.toFloat(), c.amount.toFloat()) }
        val colors = counts.map { ContextCompat.getColor(requireContext(), IdeaStatusUi.colorOf(it.status)) }
        val labels = counts.map { getString(IdeaStatusUi.labelOf(it.status)) }

        val dataSet = BarDataSet(entries, "").apply {
            this.colors = colors
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        }
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        data = BarData(dataSet)
        invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}