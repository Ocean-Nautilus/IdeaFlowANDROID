package com.nautilus.ideas

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nautilus.ideas.viewmodels.SettingsViewModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTransferTest {
    @Test
    fun corruptJsonAndInvalidUtf8HaveReadableErrors() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as IdeasApp
        for (bytes in listOf("{".toByteArray(), byteArrayOf(0xFF.toByte()))) {
            val file = File.createTempFile("backup-test-", ".json", app.cacheDir)
            try {
                file.writeBytes(bytes)
                val viewModel = SettingsViewModel(app)
                instrumentation.runOnMainSync { viewModel.importFrom(Uri.fromFile(file)) }
                val result = withTimeout(5_000) {
                    viewModel.transfer.first { !it.isBusy && it.message != null }
                }
                assertEquals(app.getString(R.string.backup_failed,
                    app.getString(R.string.backup_invalid_file)), result.message)
            } finally {
                file.delete()
            }
        }
    }
}
