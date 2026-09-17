package com.nautilus.ideas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        // id пунктов меню совпадают с id экранов в nav_graph.xml,
        // поэтому одной строки достаточно для всей навигации.
        binding.bottomNav.setupWithNavController(navHostFragment.navController)
    }
}
