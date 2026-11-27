package com.nautilus.ideas.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nautilus.ideas.data.dao.CategoryDao
import com.nautilus.ideas.data.dao.IdeaDao
import com.nautilus.ideas.data.dao.TagDao
import com.nautilus.ideas.data.model.Category
import com.nautilus.ideas.data.model.Idea
import com.nautilus.ideas.data.model.IdeaTagCrossRef
import com.nautilus.ideas.data.model.Tag

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
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun ideaDao(): IdeaDao
    abstract fun tagDao(): TagDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ideas ADD COLUMN backupId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE ideas SET backupId = lower(hex(randomblob(16)))")
            }
        }

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
                .addMigrations(MIGRATION_1_2)
                .addCallback(seedCallback())
                .build()

        /**
         * При самом первом создании файла базы наполняет её примерами,
         * чтобы приложение не открывалось пустым.
         */
        private fun seedCallback() = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DatabaseSeeder.seed(db)
            }
        }
    }
}
