package com.nautilus.ideas.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nautilus.ideas.IdeasApp
import com.nautilus.ideas.R
import com.nautilus.ideas.data.IdeaBackup
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import org.json.JSONException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TransferState(val isBusy: Boolean = false, val message: String? = null)

class SettingsViewModel(private val app: IdeasApp) : ViewModel() {
    private val _transfer = MutableStateFlow(TransferState())
    val transfer = _transfer.asStateFlow()

    fun exportTo(uri: Uri) = runTransfer {
        val json = IdeaBackup(app.database).exportJson()
        val stream = requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) {
            "Не удалось открыть файл для записи"
        }
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        app.getString(R.string.backup_exported)
    }

    fun importFrom(uri: Uri) = runTransfer {
        val stream = requireNotNull(app.contentResolver.openInputStream(uri)) { "Не удалось открыть файл" }
        val bytes = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= IdeaBackup.MAX_FILE_BYTES) { "Файл превышает 10 МБ" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        val result = IdeaBackup(app.database).importJson(text)
        app.getString(R.string.backup_imported, result.added, result.skipped)
    }

    private fun runTransfer(action: suspend () -> String) {
        if (_transfer.value.isBusy) return
        _transfer.value = TransferState(isBusy = true)
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { action() }
                _transfer.value = TransferState(message = message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = when (error) {
                    is JSONException, is CharacterCodingException -> app.getString(R.string.backup_invalid_file)
                    else -> error.localizedMessage ?: app.getString(R.string.backup_unknown_error)
                }
                _transfer.value = TransferState(message = app.getString(R.string.backup_failed, reason))
            }
        }
    }

    fun clearMessage() { _transfer.value = _transfer.value.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IdeasApp)
            }
        }
    }
}
