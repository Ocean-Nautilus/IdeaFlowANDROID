package com.nautilus.ideas.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nautilus.ideas.data.dao.CategoryDao
import com.nautilus.ideas.data.dao.IdeaDao
import com.nautilus.ideas.data.dao.TagDao
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * База данных приложения.
 *
 * Четыре таблицы: три основные (категории, идеи, теги) и одна
 * связующая (idea_tag_cross_ref) для связи многие-ко-многим.
 */
@Database(
    entities = [
        Category::class,
        Idea::class,
        Tag::class,
        IdeaTagCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun ideaDao(): IdeaDao
    abstract fun tagDao(): TagDao

    companion object {

        private const val DATABASE_NAME = "ideas.db"

        // @Volatile: значение сразу видно всем потокам, а не только тому,
        // который его записал.
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Возвращает единственный экземпляр базы.
         *
         * Открывать базу дважды нельзя: два соединения будут мешать
         * друг другу блокировками. Двойная проверка с synchronized
         * гарантирует, что при одновременном обращении из разных
         * потоков объект создастся ровно один раз.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                // На время учебной разработки структура таблиц ещё меняется.
                // Вместо написания миграции на каждую правку база просто
                // пересоздаётся. Перед сдачей это нужно заменить на миграции.
                .fallbackToDestructiveMigration()
                .addCallback(seedCallback(context))
                .build()

        /**
         * При самом первом создании файла базы наполняет её примерами,
         * чтобы приложение не открывалось пустым.
         */
        private fun seedCallback(context: Context) = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // onCreate вызывается при первом обращении к базе, то есть
                // уже после того, как build() вернул объект и instance
                // присвоен — рекурсии здесь не будет.
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    DatabaseSeeder.seed(getInstance(context))
                }
            }
        }
    }
}
