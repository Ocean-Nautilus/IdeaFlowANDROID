package com.nautilus.ideas

import com.nautilus.ideas.data.model.IdeaStatus
import com.nautilus.ideas.data.model.StatusCount
import com.nautilus.ideas.viewmodels.StatsUiState
import org.junit.Assert.*
import org.junit.Test

class StatsUiStateTest {
    @Test
    fun summaryIncludesAllIdeasAndShowsZeroForMissingStatuses() {
        val state = StatsUiState(isLoading = false, total = 7, statusCounts = listOf(
            StatusCount(IdeaStatus.NEW, 3), StatusCount(IdeaStatus.IN_PROGRESS, 2),
            StatusCount(IdeaStatus.POSTPONED, 2)))
        assertEquals(7, state.total)
        assertEquals(3, state.statusCount(IdeaStatus.NEW))
        assertEquals(2, state.statusCount(IdeaStatus.IN_PROGRESS))
        assertEquals(0, state.statusCount(IdeaStatus.DONE))
        assertFalse(state.isEmpty)
        val empty = StatsUiState(isLoading = false)
        assertTrue(empty.isEmpty)
        IdeaStatus.entries.forEach { assertEquals(0, empty.statusCount(it)) }
    }
}
