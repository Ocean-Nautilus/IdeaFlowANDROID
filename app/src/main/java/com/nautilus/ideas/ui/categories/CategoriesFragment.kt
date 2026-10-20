package com.nautilus.ideas.ui.categories

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.view.Gravity
import androidx.core.view.WindowCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nautilus.ideas.R
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.data.model.CategoryCount
import com.nautilus.ideas.databinding.FragmentCategoriesBinding
import com.nautilus.ideas.viewmodels.CategoriesViewModel
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment(R.layout.fragment_categories) {
    private val viewModel: CategoriesViewModel by viewModels { CategoriesViewModel.Factory }
    private var rendering = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentCategoriesBinding.bind(view)
        val colorLabels = resources.getStringArray(R.array.category_colors)
        val settings = (requireActivity().application as IdeasApp).settings
        fun refreshPalette() {
            val colors = COLORS.zip(colorLabels.toList()).toMap() + settings.categoryColors
            colors.forEach { (color, label) ->
                val chip = ui.colorChoices.findViewWithTag<Chip>(color)
                if (chip == null) ui.colorChoices.addView(colorChip(label, color)) else chip.text = label
            }
        }
        refreshPalette()
        childFragmentManager.setFragmentResultListener(CategoryColorDialog.RESULT_KEY, viewLifecycleOwner) { _, result ->
            refreshPalette()
            result.getString("color")?.let(viewModel::colorChanged)
        }
        ui.createColorButton.setOnClickListener {
            if (childFragmentManager.findFragmentByTag("colorPicker") == null) {
                CategoryColorDialog().apply { arguments = bundleOf("color" to viewModel.uiState.value.color) }
                    .show(childFragmentManager, "colorPicker")
            }
        }
        ui.colorChoices.setOnCheckedStateChangeListener { group, ids ->
            if (!rendering) ids.firstOrNull()?.let { id ->
                viewModel.colorChanged(group.findViewById<Chip>(id).tag as String)
            }
        }
        val back = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            if (!viewModel.uiState.value.isBusy) viewModel.cancelEdit()
        }
        ui.closeButton.setOnClickListener {
            if (viewModel.uiState.value.isFormOpen) viewModel.cancelEdit() else findNavController().navigateUp()
        }
        ui.addCategoryButton.setOnClickListener { viewModel.add() }
        ui.categoryNameInput.doAfterTextChanged { if (!rendering) viewModel.nameChanged(it.toString()) }
        ui.saveCategoryButton.setOnClickListener { viewModel.save(getString(R.string.category_saved)) }
        ui.cancelCategoryButton.setOnClickListener { viewModel.cancelEdit() }
        ui.deleteCategoryButton.setOnClickListener {
            val state = viewModel.uiState.value
            val row = state.rows.find { it.categoryId == state.editingId } ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category_delete_title)
                .setMessage(getString(R.string.category_delete_message, row.categoryName, row.amount))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.delete(row, getString(R.string.category_deleted))
                }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previousRows: List<CategoryCount>? = null
                var previousBusy: Boolean? = null
                var previousFormOpen: Boolean? = null
                viewModel.uiState.collect { state ->
                    rendering = true
                    if (ui.categoryNameInput.text.toString() != state.name) ui.categoryNameInput.setText(state.name)
                    val selectedColor = state.color.uppercase()
                    if (ui.colorChoices.findViewWithTag<Chip>(selectedColor) == null) {
                        ui.colorChoices.addView(colorChip(getString(R.string.category_current_color), selectedColor))
                    }
                    for (index in 0 until ui.colorChoices.childCount) {
                        (ui.colorChoices.getChildAt(index) as Chip).apply {
                            isChecked = tag == selectedColor
                            chipStrokeWidth = if (isChecked) 2 * resources.displayMetrics.density else 0f
                            isEnabled = !state.isBusy
                        }
                    }
                    rendering = false
                    back.isEnabled = state.isFormOpen
                    ui.closeButton.isEnabled = !state.isBusy
                    ui.listPanel.visibility = if (state.isFormOpen) View.GONE else View.VISIBLE
                    ui.formPanel.visibility = if (state.isFormOpen) View.VISIBLE else View.GONE
                    ui.categoryHeader.setText(when {
                        !state.isFormOpen -> R.string.categories_title
                        state.editingId == 0L -> R.string.category_new_title
                        else -> R.string.category_edit
                    })
                    if (previousFormOpen != state.isFormOpen) {
                        ui.categoryScroll.scrollTo(0, 0)
                        if (!state.isFormOpen) {
                            ui.categoryNameInput.clearFocus()
                            WindowCompat.getInsetsController(requireActivity().window, ui.root).hide(WindowInsetsCompat.Type.ime())
                        }
                        previousFormOpen = state.isFormOpen
                    }
                    ui.deleteCategoryButton.visibility = if (state.editingId == 0L) View.GONE else View.VISIBLE
                    ui.deleteCategoryButton.isEnabled = !state.isBusy && state.rows.size > 1
                    ui.lastCategoryHint.visibility = if (state.editingId != 0L && state.rows.size == 1) View.VISIBLE else View.GONE
                    ui.saveCategoryButton.isEnabled = !state.isBusy && state.name.isNotBlank()
                    ui.cancelCategoryButton.isEnabled = !state.isBusy
                    ui.createColorButton.isEnabled = !state.isBusy
                    ui.categoryNameInput.isEnabled = !state.isBusy
                    ui.categoryProgress.visibility = if (state.isBusy) View.VISIBLE else View.GONE
                    if (previousRows != state.rows || previousBusy != state.isBusy) {
                        ui.categoryRows.removeAllViews()
                        state.rows.forEach { row -> addRow(ui, row, state.isBusy) }
                        previousRows = state.rows
                        previousBusy = state.isBusy
                    }
                    state.message?.let {
                        Snackbar.make(ui.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.clearMessage()
                    }
                }
            }
        }
    }

    private fun colorChip(label: String, color: String) = Chip(requireContext()).apply {
        id = View.generateViewId()
        text = label
        tag = color
        isCheckable = true
        isSaveEnabled = false
        chipIcon = colorDot(color)
        chipIconTint = null
        checkedIcon = null
        setEnsureMinTouchTargetSize(true)
    }

    private fun colorDot(color: String) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(runCatching { Color.parseColor(color) }.getOrDefault(Color.GRAY))
        val size = (16 * resources.displayMetrics.density).toInt()
        setBounds(0, 0, size, size)
    }

    private fun addRow(ui: FragmentCategoriesBinding, row: CategoryCount, busy: Boolean) {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        block.addView(TextView(context).apply {
            text = row.categoryName
            textSize = 18f
            setTextColor(context.getColor(R.color.text_primary))
            setCompoundDrawablesRelative(colorDot(row.colorHex), null, null, null)
            compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
        })
        block.addView(TextView(context).apply {
            text = getString(R.string.category_count, row.amount)
            setTextColor(context.getColor(R.color.text_secondary))
        })
        ui.categoryRows.addView(MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
            radius = 12 * density
            cardElevation = 0f
            setCardBackgroundColor(context.getColor(R.color.surface))
            isClickable = true
            isFocusable = true
            isEnabled = !busy
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(block, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.text_secondary))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                    marginEnd = (16 * density).toInt()
                })
            })
            setOnClickListener { viewModel.edit(row); ui.categoryScroll.smoothScrollTo(0, 0) }
        })
    }

    companion object {
        private val COLORS = listOf("#4DD0C4", "#D2F84B", "#5CD16A", "#64B5F6", "#FFB74D", "#BA68C8")
    }
}
