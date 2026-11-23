package jp.yukirawa.launotor

import android.app.Application
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import jp.yukirawa.launotor.data.repository.MachineRepository
import jp.yukirawa.launotor.data.repository.launotorDataStore
import jp.yukirawa.launotor.notifications.NotificationChannels

class LaunotorApplication : Application() {
    val repository: MachineRepository by lazy {
        LocalMachineRepository(launotorDataStore)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
    }
}
