package com.nautilus.ideas

import com.nautilus.ideas.data.IdeaSearch
import com.nautilus.ideas.data.model.*
import org.junit.Assert.*
import org.junit.Test

class IdeaSearchTest {
    private val category = Category(id = 1, name = "Учёба", colorHex = "#123456")
    private fun item(title: String, content: String = "", categoryId: Long = 1) =
        IdeaWithDetails(Idea(title = title, content = content, categoryId = categoryId), category, emptyList())

    @Test fun russianSearchIsCaseInsensitiveAndWildcardsAreLiteral() {
        val notes = listOf(item("Курсовая", "ПРОЕКТ"), item("100% готово"), item("имя_файла"))
        assertEquals(listOf(notes[0]), IdeaSearch.filter(notes, "курсовая", null, null, SortOption.DATE_DESC))
        assertEquals(listOf(notes[0]), IdeaSearch.filter(notes, "проект", null, null, SortOption.DATE_DESC))
        assertEquals(listOf(notes[1]), IdeaSearch.filter(notes, "%", null, null, SortOption.DATE_DESC))
        assertEquals(listOf(notes[2]), IdeaSearch.filter(notes, "_", null, null, SortOption.DATE_DESC))
    }

    @Test fun russianAlphabetAndFiltersWorkTogether() {
        val notes = listOf(item("яблоко"), item("Банан"), item("арбуз"), item("Ананас", categoryId = 2))
        val sorted = IdeaSearch.filter(notes, "", null, 1, SortOption.TITLE_ASC)
        assertEquals(listOf("арбуз", "Банан", "яблоко"), sorted.map { it.idea.title })
        assertTrue(IdeaSearch.filter(notes, "", IdeaStatus.DONE, null, SortOption.TITLE_ASC).isEmpty())
    }
}
