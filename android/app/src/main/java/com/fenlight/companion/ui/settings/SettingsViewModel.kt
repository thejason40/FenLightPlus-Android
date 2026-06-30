package com.fenlight.companion.ui.settings

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenlight.companion.BuildConfig
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.update.UpdateChecker
import com.fenlight.companion.data.update.UpdateInfo
import com.fenlight.companion.data.update.UpdateResult
import com.fenlight.companion.util.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UpdateUiState(
    val checking: Boolean = false,
    val available: Boolean = false,
    val upToDate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val error: String? = null,
    val downloading: Boolean = false,
)

data class SettingsUiState(
    val checkUpdateOnStartup: Boolean = true,
    val region: String = "",
    val excludeAdult: Boolean = true,
    val themeMode: String = "system",
    val sourceSelection: String = "ask",
    val update: UpdateUiState = UpdateUiState(),
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FenLightApp
    private val prefs = app.prefs
    private val checker = UpdateChecker()

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.checkUpdateOnStartup.collect { enabled ->
                _state.update { it.copy(checkUpdateOnStartup = enabled) }
            }
        }
        viewModelScope.launch {
            prefs.region.collect { r -> _state.update { it.copy(region = r) } }
        }
        viewModelScope.launch {
            prefs.excludeAdult.collect { e -> _state.update { it.copy(excludeAdult = e) } }
        }
        viewModelScope.launch {
            prefs.themeMode.collect { m -> _state.update { it.copy(themeMode = m) } }
        }
        viewModelScope.launch {
            prefs.sourceSelection.collect { s -> _state.update { it.copy(sourceSelection = s) } }
        }
    }

    fun toggleCheckUpdateOnStartup(enabled: Boolean) {
        viewModelScope.launch { prefs.setCheckUpdateOnStartup(enabled) }
    }

    fun setRegion(region: String) {
        viewModelScope.launch { prefs.setRegion(region) }
    }

    fun toggleExcludeAdult(enabled: Boolean) {
        viewModelScope.launch { prefs.setExcludeAdult(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setSourceSelection(mode: String) {
        viewModelScope.launch { prefs.setSourceSelection(mode) }
    }

    fun clearTraktCwCache() {
        viewModelScope.launch { prefs.saveTraktCwCache("") }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(update = UpdateUiState(checking = true)) }
            _state.update {
                it.copy(
                    update = when (val result = checker.check(BuildConfig.VERSION_CODE)) {
                        is UpdateResult.Available -> UpdateUiState(available = true, updateInfo = result.info)
                        UpdateResult.UpToDate -> UpdateUiState(upToDate = true)
                        is UpdateResult.Error -> UpdateUiState(error = result.message)
                    }
                )
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        val context = getApplication<Application>()
        val destFile = File(context.getExternalFilesDir(null), "FenLightCompanion-update.apk")
        if (destFile.exists()) destFile.delete()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("FenLight+ Companion update")
            .setDescription("Downloading…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destFile))
        val downloadId = dm.enqueue(request)
        _state.update { it.copy(update = it.update.copy(downloading = true)) }

        // Poll the DownloadManager rather than relying on ACTION_DOWNLOAD_COMPLETE.
        // That broadcast is sent by the system process, so on Android 13+ a
        // context-registered receiver only receives it with RECEIVER_EXPORTED, and it
        // can be missed entirely if the app isn't running. Polling is reliable everywhere.
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val (status, reason) = queryDownload(dm, downloadId)
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    onDownloadFinished(context, info, destFile)
                    return@launch
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    destFile.delete()
                    _state.update {
                        it.copy(update = UpdateUiState(error = "Download failed (code $reason)"))
                    }
                    return@launch
                }
                delay(750)
            }
        }
    }

    private fun queryDownload(dm: DownloadManager, downloadId: Long): Pair<Int, Int> {
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadManager.STATUS_FAILED to -1
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return status to reason
        }
    }

    private fun onDownloadFinished(context: Context, info: UpdateInfo, destFile: File) {
        val expected = info.sha256?.trim()?.lowercase()
        if (expected != null && sha256Of(destFile) != expected) {
            destFile.delete()
            // Reset to error-only state so the screen shows the failure, not the update offer
            _state.update {
                it.copy(update = UpdateUiState(error = "Update failed integrity check — download discarded"))
            }
            return
        }
        _state.update { it.copy(update = it.update.copy(downloading = false)) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun sha256Of(file: File): String = sha256Hex(file.inputStream())
}
