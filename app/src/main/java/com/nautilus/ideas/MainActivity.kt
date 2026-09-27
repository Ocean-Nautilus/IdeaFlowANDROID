package com.nautilus.ideas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.nautilus.ideas.databinding.ActivityMainBinding

/**
 * Единственная Activity приложения.
 *
 * Она не содержит бизнес-логики: её задача - показать контейнер для
 * фрагментов и связать нижнее меню с графом навигации.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NavHostFragment ищем именно через FragmentManager:
        // findViewById здесь вернёт View, а не сам фрагмент.
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // id пунктов меню совпадают с id экранов в nav_graph.xml,
        // поэтому одной строки достаточно для всей навигации.
        binding.bottomNav.setupWithNavController(navController)

        // Нижнее меню нужно только на вкладках. На форме редактирования
        // оно мешает: экран открывается поверх списка, и переключаться
        // на статистику из наполовину заполненной формы бессмысленно.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTab = destination.id in TAB_DESTINATIONS
            binding.bottomNav.isVisible = isTab
            binding.navDivider.isVisible = isTab
        }
    }

    private companion object {
        val TAB_DESTINATIONS = setOf(
            R.id.ideasFragment,
            R.id.statsFragment,
            R.id.settingsFragment
        )
    }
}
