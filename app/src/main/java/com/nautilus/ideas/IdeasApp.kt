package com.nautilus.ideas

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.nautilus.ideas.data.db.AppDatabase
import com.nautilus.ideas.data.repository.IdeaRepository

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

    override fun onCreate() {
        super.onCreate()

        // Тёмная тема по умолчанию - как на макетах.
        // Неделя 10 заменит это значение на сохранённое в настройках.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
