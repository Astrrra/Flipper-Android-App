package com.flipperdevices.updater.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipperdevices.bridge.synchronization.api.SynchronizationApi
import com.flipperdevices.bridge.synchronization.api.SynchronizationState
import com.flipperdevices.core.di.ComponentHolder
import com.flipperdevices.core.ktx.jre.launchWithLock
import com.flipperdevices.core.log.LogTagProvider
import com.flipperdevices.core.log.info
import com.flipperdevices.updater.api.UpdaterApi
import com.flipperdevices.updater.model.UpdatingState
import com.flipperdevices.updater.model.VersionFiles
import com.flipperdevices.updater.ui.di.UpdaterComponent
import com.flipperdevices.updater.ui.fragments.CancelDialogBuilder
import com.flipperdevices.updater.ui.model.UpdaterScreenState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex

class UpdaterViewModel : ViewModel(), LogTagProvider {
    override val TAG = "UpdaterViewModel"

    private val updaterScreenState = MutableStateFlow<UpdaterScreenState>(
        UpdaterScreenState.NotStarted
    )
    private val mutex = Mutex()
    private var updaterJob: Job? = null

    @Inject
    lateinit var updaterApi: UpdaterApi

    @Inject
    lateinit var synchronizationApi: SynchronizationApi

    init {
        ComponentHolder.component<UpdaterComponent>().inject(this)
        updaterJob = subscribeOnUpdaterFlow()
    }

    fun getState(): StateFlow<UpdaterScreenState> = updaterScreenState

    fun start(
        versionFiles: VersionFiles?
    ) = launchWithLock(mutex, viewModelScope, "start") {
        if (versionFiles == null) {
            if (!updaterApi.isUpdateInProcess()) {
                updaterScreenState.emit(UpdaterScreenState.Finish)
            }
            return@launchWithLock
        }

        updaterScreenState.emit(UpdaterScreenState.CancelingSynchronization(versionFiles.version))
        synchronizationApi.stop()

        info {
            "Wait until synchronization end. " +
                "Current state is ${synchronizationApi.getSynchronizationState().value}"
        }

        // Wait until synchronization is really canceled
        synchronizationApi.getSynchronizationState()
            .filter { it == SynchronizationState.NotStarted || it == SynchronizationState.Finished }
            .first()

        info { "Start updating" }

        updaterApi.start(versionFiles)
    }

    fun cancel() {
        CancelDialogBuilder.showDialog { cancelInternal() }
    }

    fun cancelInternal() = launchWithLock(mutex, viewModelScope, "cancel") {
        updaterJob?.cancelAndJoin()
        updaterJob = null
        updaterScreenState.emit(UpdaterScreenState.CancelingUpdate)
        updaterApi.cancel()

        info { "Wait until updating end" }

        // Wait until update is really canceled
        updaterApi.getState()
            .filter { it.state == UpdatingState.NotStarted || it.state == UpdatingState.Rebooting }
            .first()
        updaterScreenState.emit(UpdaterScreenState.Finish)
    }

    private fun subscribeOnUpdaterFlow(): Job = updaterApi.getState().onEach {
        val version = it.version
        val state = it.state
        updaterScreenState.emit(
            when (state) {
                UpdatingState.NotStarted -> UpdaterScreenState.NotStarted
                is UpdatingState.DownloadingFromNetwork ->
                    UpdaterScreenState.DownloadingFromNetwork(
                        percent = state.percent,
                        version = version
                    )
                is UpdatingState.UploadOnFlipper ->
                    UpdaterScreenState.UploadOnFlipper(
                        percent = state.percent,
                        version = version
                    )
                UpdatingState.Rebooting -> UpdaterScreenState.Finish
            }
        )
    }.launchIn(viewModelScope)
}
