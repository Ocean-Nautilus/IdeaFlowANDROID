package com.nautilus.ideas

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationTest {
    @Test fun migrationKeepsIdeasCategoriesAndTags(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-${System.nanoTime()}.db"
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.nautilus.ideas.data.db.AppDatabase/1.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.getJSONArray("indices")
                    for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                db.execSQL("INSERT INTO categories VALUES(1, 'Учёба', '#123456', 100)")
                db.execSQL("INSERT INTO ideas VALUES(1, 'Курсовая', 'Текст', 1, 'NEW', 4, 0, 100, 100)")
                db.execSQL("INSERT INTO tags VALUES(1, 'проект')")
                db.execSQL("INSERT INTO idea_tag_cross_ref VALUES(1, 1)")
                db.version = 1
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_2).build()
            try {
                val item = requireNotNull(db.ideaDao().getById(1))
                assertEquals("Курсовая", item.idea.title)
                assertEquals("Текст", item.idea.content)
                assertEquals("Учёба", item.category.name)
                assertEquals(listOf("проект"), item.tags.map { it.name })
                assertEquals(32, item.idea.backupId.length)
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
