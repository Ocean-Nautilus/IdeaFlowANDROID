package com.nautilus.ideas.ui.categories

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.R
import com.nautilus.ideas.databinding.DialogCategoryColorBinding
import java.util.Locale

class CategoryColorDialog : DialogFragment() {
    private var color = Color.CYAN
    private var colorName = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val settings = (requireActivity().application as IdeasApp).settings
        color = savedInstanceState?.getInt("color") ?: runCatching {
            Color.parseColor(requireArguments().getString("color"))
        }.getOrDefault(Color.CYAN)
        colorName = savedInstanceState?.getString("name")
            ?: getString(R.string.palette_default_name, settings.categoryColors.size + 1)
        val ui = DialogCategoryColorBinding.inflate(layoutInflater)
        ui.colorNameInput.setText(colorName)
        ui.redSlider.value = Color.red(color).toFloat()
        ui.greenSlider.value = Color.green(color).toFloat()
        ui.blueSlider.value = Color.blue(color).toFloat()
        fun preview() {
            color = Color.rgb(ui.redSlider.value.toInt(), ui.greenSlider.value.toInt(), ui.blueSlider.value.toInt())
            ui.colorPreview.setBackgroundColor(color)
        }
        preview()
        listOf(ui.redSlider, ui.greenSlider, ui.blueSlider).forEach { slider ->
            slider.addOnChangeListener { _, _, _ -> preview() }
        }
        ui.colorNameInput.doAfterTextChanged {
            colorName = it.toString()
            ui.colorNameLayout.error = null
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.palette_create)
            .setView(ui.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create().apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val hex = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
                        try {
                            settings.saveCategoryColor(colorName, hex)
                            parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf("color" to hex))
                            dismiss()
                        } catch (error: IllegalArgumentException) {
                            ui.colorNameLayout.error = error.message
                        }
                    }
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("color", color)
        outState.putString("name", colorName)
        super.onSaveInstanceState(outState)
    }

    companion object { const val RESULT_KEY = "categoryColor" }
}
