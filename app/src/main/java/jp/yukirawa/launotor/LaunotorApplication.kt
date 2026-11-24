package jp.yukirawa.launotor

import android.app.Application
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import jp.yukirawa.launotor.data.repository.MachineRepository
import jp.yukirawa.launotor.data.repository.launotorDataStore
import jp.yukirawa.launotor.notifications.NotificationChannels
import jp.yukirawa.launotor.sync.P2pSyncCoordinator
import jp.yukirawa.launotor.sync.SyncDeviceIdProvider

class LaunotorApplication : Application() {
    val syncDeviceIdProvider: SyncDeviceIdProvider by lazy {
        SyncDeviceIdProvider(this)
    }

    val repository: MachineRepository by lazy {
        LocalMachineRepository(
            dataStore = launotorDataStore,
            localOriginHash = syncDeviceIdProvider.originHash,
        )
    }

    val p2pSyncCoordinator: P2pSyncCoordinator by lazy {
        P2pSyncCoordinator(
            context = this,
            repository = repository,
            deviceIdProvider = syncDeviceIdProvider,
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
    }
}
