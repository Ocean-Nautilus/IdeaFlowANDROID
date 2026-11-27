package com.nautilus.ideas.data

import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.IdeaWithDetails
import com.nautilus.ideas.data.model.SortOption
import java.text.Collator
import java.util.Locale

object IdeaSearch {
    // shortcut: фильтрация в памяти подходит для личного блокнота; для больших баз нужен поисковый индекс.
    fun filter(items: List<IdeaWithDetails>, query: String, status: IdeaStatus?, categoryId: Long?, sort: SortOption): List<IdeaWithDetails> {
        val found = items.filter {
            (status == null || it.idea.status == status) &&
                (categoryId == null || it.idea.categoryId == categoryId) &&
                (it.idea.title.contains(query, ignoreCase = true) || it.idea.content.contains(query, ignoreCase = true))
        }
        return when (sort) {
            SortOption.DATE_DESC -> found.sortedByDescending { it.idea.createdAt }
            SortOption.DATE_ASC -> found.sortedBy { it.idea.createdAt }
            SortOption.RATING_DESC -> found.sortedWith(compareByDescending<IdeaWithDetails> { it.idea.rating }.thenByDescending { it.idea.createdAt })
            SortOption.TITLE_ASC -> {
                val collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.SECONDARY }
                found.sortedWith { a, b -> collator.compare(a.idea.title, b.idea.title).takeIf { it != 0 }
                    ?: b.idea.createdAt.compareTo(a.idea.createdAt) }
            }
        }
    }
}
