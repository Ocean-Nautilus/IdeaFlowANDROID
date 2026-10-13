package com.nautilus.ideas.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.databinding.ItemIdeaBinding
import com.nautilus.ideas.utils.DateFormatter
import com.nautilus.ideas.utils.IdeaStatusUi
import com.nautilus.ideas.R

/**
 * Адаптер списка идей.
 *
 * Наследуется от ListAdapter, а не от голого RecyclerView.Adapter:
 * ListAdapter сам считает разницу между старым и новым списком в фоновом
 * потоке и перерисовывает только изменившиеся строки. Благодаря этому
 * при добавлении одной идеи список не моргает целиком.
 *
 * @param onIdeaClick вызывается при нажатии на карточку
 */
class IdeaAdapter(
    private val onIdeaClick: (IdeaWithDetails) -> Unit
) : ListAdapter<IdeaWithDetails, IdeaAdapter.IdeaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IdeaViewHolder {
        val binding = ItemIdeaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IdeaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IdeaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class IdeaViewHolder(
        private val binding: ItemIdeaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Слушатель вешается один раз при создании ViewHolder, а не в bind:
            // bind вызывается при каждой прокрутке, и создавать там новый
            // объект-слушатель на каждую строку — лишняя работа.
            binding.root.setOnClickListener {
                // NO_POSITION приходит, когда строка уже удалена,
                // но анимация исчезновения ещё идёт.
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onIdeaClick(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: IdeaWithDetails) = with(binding) {
            val context = root.context

            titleText.text = item.idea.title
            contentText.text = item.idea.content
            categoryText.text = item.category.name
            dateText.text = DateFormatter.formatShort(item.idea.createdAt)

            // Цвет категории хранится строкой вида #RRGGBB. Если строка
            // окажется испорченной, берём серый вместо падения приложения.
            val categoryColor = runCatching { Color.parseColor(item.category.colorHex) }
                .getOrDefault(Color.GRAY)
            categoryDot.backgroundTintList = ColorStateList.valueOf(categoryColor)

            statusText.setText(IdeaStatusUi.labelOf(item.idea.status))
            statusText.setTextColor(
                ContextCompat.getColor(context, IdeaStatusUi.colorOf(item.idea.status))
            )

            // Описание может быть пустым — тогда строку под заголовком прячем,
            // иначе карточка получит пустой отступ непонятного назначения.
            contentText.visibility = if (item.idea.content.isBlank()) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }

            val litColor = ContextCompat.getColor(context, R.color.accent)
            val unlitColor = ContextCompat.getColor(context, R.color.divider)
            listOf(lamp1, lamp2, lamp3, lamp4, lamp5).forEachIndexed { index, lamp ->
                lamp.imageTintList = ColorStateList.valueOf(
                    if (index < item.idea.rating) litColor else unlitColor
                )
            }
        }
    }

    companion object {
        /**
         * Правила сравнения строк списка.
         *
         * areItemsTheSame — та же самая идея (сравниваем по id).
         * areContentsTheSame — у неё ничего не изменилось (сравниваем целиком,
         * data class умеет это сам).
         */
        private val DIFF = object : DiffUtil.ItemCallback<IdeaWithDetails>() {
            override fun areItemsTheSame(old: IdeaWithDetails, new: IdeaWithDetails): Boolean =
                old.idea.id == new.idea.id

            override fun areContentsTheSame(old: IdeaWithDetails, new: IdeaWithDetails): Boolean =
                old == new
        }
    }
}
