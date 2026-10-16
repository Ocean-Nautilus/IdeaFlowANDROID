package com.nautilus.ideas

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.repository.IdeaRepository
import com.nautilus.ideas.data.AppSettings

/**
 * Класс приложения. Создаётся один раз при запуске, до любой Activity.
 *
 * Здесь живут объекты, которые должны существовать в единственном
 * экземпляре и переживать все экраны: база данных и репозиторий.
 */
class IdeasApp : Application() {

    /**
     * by lazy: база откроется не при старте приложения, а при первом
     * обращении к ней. Запуск от этого не тормозит.
     */
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: IdeaRepository by lazy { IdeaRepository.from(database) }
    val settings: AppSettings by lazy { AppSettings(getSharedPreferences("settings", MODE_PRIVATE)) }

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(settings.themeMode)
    }
}
