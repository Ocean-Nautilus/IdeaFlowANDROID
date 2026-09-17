package com.nautilus.ideas

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Класс приложения. Создаётся один раз при запуске, до любой Activity.
 *
 * Сейчас здесь только выбор темы. На неделе 2 сюда добавится создание
 * базы данных, а на неделе 9 тема начнёт браться из настроек пользователя.
 */
class IdeasApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Тёмная тема по умолчанию - как на макетах.
        // Неделя 9 заменит это значение на сохранённое в настройках.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
