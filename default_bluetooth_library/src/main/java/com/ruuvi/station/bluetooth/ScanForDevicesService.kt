package com.ruuvi.station.bluetooth

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.kodein
import org.kodein.di.generic.instance
import timber.log.Timber
import java.util.*
import kotlin.concurrent.schedule

class ScanForDevicesService : JobIntentService(), KodeinAware {

    override val kodein: Kodein by kodein()
    private val bluetoothInteractor: BluetoothInteractor by instance()

    override fun onHandleWork(p0: Intent) {

        Timber.d("onHandleWork")

        bluetoothInteractor.startScan()

        Timer(false).schedule(bluetoothInteractor.getWorkTime()) {
            bluetoothInteractor.stopScanningFromBackground()
        }
    }

    companion object {

        private const val JOB_ID = 1000

        fun enqueueWork(context: Context) {
            enqueueWork(
                    context,
                    ScanForDevicesService::class.java,
                    JOB_ID,
                    Intent()
            )
        }
    }
}