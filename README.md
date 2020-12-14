# com.ruuvi.bluetooth.default
Default implementation of Ruuvi Bluetooth library for Android

### Ruuvi sensor protocol specification:
https://github.com/ruuvi/ruuvi-sensor-protocols

# Usage example

Add the JitPack repository to your projects build.gradle file
```gradle
allprojects {
    repositories {
        maven { url "https://www.jitpack.io" }
        ...
    }
}
```

Add the library and its interface to your project by adding following as dependencies in app/build.gradle
```gradle
dependencies {
    implementation 'com.github.ruuvi:com.ruuvi.bluetooth:2cedd919b2'
    implementation 'com.github.ruuvi:com.ruuvi.bluetooth.default:31e717b'
    ...
}
```

Add bluetooth and location permission in AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

You are now ready to use the library. You can get started by initializing the RuuviRangeNotifier, requesting location permission and start the scanning. You activity could look something like this:
```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ruuvi.station.bluetooth.FoundRuuviTag
import com.ruuvi.station.bluetooth.IRuuviTagScanner
import com.ruuvi.station.bluetooth.RuuviRangeNotifier

class MainActivity : AppCompatActivity(), IRuuviTagScanner.OnTagFoundListener {
    private lateinit var ruuviRangeNotifier: IRuuviTagScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ruuviRangeNotifier = RuuviRangeNotifier(application, "MainActivity")
    }

    override fun onResume() {
        super.onResume()
        requestLocationPermission()
    }

    override fun onPause() {
        super.onPause()
        ruuviRangeNotifier.stopScanning()
    }

    private val FINE_LOCATION_PERMISSION_REQUEST = 1337
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), FINE_LOCATION_PERMISSION_REQUEST)
        } else {
            startScanning()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            FINE_LOCATION_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startScanning()
                } else {
                    // notify the user to allow location detection otherwise the scanning won't work (except on a few devices)
                }
                return
            }
        }
    }

    fun startScanning() {
        ruuviRangeNotifier.startScanning(this)
    }

    override fun onTagFound(tag: FoundRuuviTag) {
        // Found tags will appear here
        Log.d(tag.id, tag.temperature.toString())
    }
}
```
