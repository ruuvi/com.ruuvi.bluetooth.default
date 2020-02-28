package com.ruuvi.station.bluetooth

import android.content.Context
import android.content.Intent
import android.support.v4.app.JobIntentService
import timber.log.Timber
import java.util.*
import kotlin.concurrent.schedule

class ScanForDevicesService : JobIntentService(){
    override fun onHandleWork(p0: Intent) {
        Timber.d("onHandleWork")
        val bluetoothInteractor = BluetoothLibrary.getBluetoothInteractor()
        bluetoothInteractor.startScan()
        Timer( false).schedule(4000) {
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