package com.ruuvi.station.bluetooth;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

/**
 *
 * This class provides relief for Android Bug 67272.  This bug in the Bluedroid stack causes crashes
 * in Android's BluetoothService when scanning for BLE devices encounters a large number of unique
 * devices.  It is rare for most users but can be problematic for those with apps scanning for
 * Bluetooth LE devices in the background (e.g. beacon-enabled apps), especially when these users
 * are around Bluetooth LE devices that randomize their mac address like Gimbal beacons.
 *
 * This class can both recover from crashes and prevent crashes from happening in the first place.
 *
 * More details on the bug can be found at the following URLs:
 *
 * https://code.google.com/p/android/issues/detail?id=67272
 * https://github.com/RadiusNetworks/android-ibeacon-service/issues/16
 *
 * Version 1.0
 *
 * Created by dyoung on 3/24/14.
 */
public class BluetoothCrashResolver {
    private static final boolean PREEMPTIVE_ACTION_ENABLED = true;
    /**
     * This is not the same file that Bluedroid uses.  This is just to maintain state of this module.
     */
    private static final String DISTINCT_BLUETOOTH_ADDRESSES_FILE = "BluetoothCrashResolverState.txt";
    private boolean recoveryInProgress = false;
    private boolean discoveryStartConfirmed = false;

    private long lastBluetoothOffTime = 0L;
    private long lastBluetoothTurningOnTime = 0L;
    private long lastBluetoothCrashDetectionTime = 0L;
    private int detectedCrashCount = 0;
    private int recoveryAttemptCount = 0;
    private boolean lastRecoverySucceeded = false;
    private long lastStateSaveTime = 0L;
    private static final long MIN_TIME_BETWEEN_STATE_SAVES_MILLIS = 60000L;

    private Context context = null;
    private UpdateNotifier updateNotifier;
    private final Set<String> distinctBluetoothAddresses = new HashSet<String>();
    /**
     // It is very likely a crash if Bluetooth turns off and comes
     // back on in an extremely short interval.  Testing on a Nexus 4 shows
     // that when the BluetoothService crashes, the time between the STATE_OFF
     // and the STATE_TURNING_ON ranges from 0ms-684ms
     // Out of 3614 samples:
     //  99.4% (3593) < 600 ms
     //  84.7% (3060) < 500 ms
     // So we will assume any power off sequence of < 600ms to be a crash
     //
     // While it is possible to manually turn Bluetooth off then back on in
     // about 600ms, but it is pretty hard to do.
     //
     */
    private static final long SUSPICIOUSLY_SHORT_BLUETOOTH_OFF_INTERVAL_MILLIS = 600L;
    /**
     * The Bluedroid stack can only track only 1990 unique Bluetooth mac addresses without crashing
     */
    private static final int BLUEDROID_MAX_BLUETOOTH_MAC_COUNT = 1990;
    /**
     * The discovery process will pare back the mac address list to 256, but more may
     * be found in the time we let the discovery process run, depending hon how many BLE
     * devices are around.
     */
    private static final int BLUEDROID_POST_DISCOVERY_ESTIMATED_BLUETOOTH_MAC_COUNT = 400;
    /**
     * It takes a little over 2 seconds after discovery is started before the pared-down mac file
     * is written to persistent storage.  We let discovery run for a few more seconds just to be
     * sure.
     */
    private static final int TIME_TO_LET_DISCOVERY_RUN_MILLIS = 5000;  /* if 0, it means forever */

    /**
     * Constructor should be called only once per long-running process that does Bluetooth LE
     * scanning.  Must call start() to make it do anything.
     *
     * @param context the Activity or Service that is doing the Bluetooth scanning
     */
    public BluetoothCrashResolver(Context context) {
        this.context = context.getApplicationContext();
        loadState();
    }

    /**
     * Starts looking for crashes of the Bluetooth LE system and taking proactive steps to stop
     * crashes from happening.  Proactive steps require calls to notifyScannedDevice(Device device)
     * so that crashes can be predicted ahead of time.
     */
    public void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);

        Timber.d("started listening for BluetoothAdapter events");
    }

    /**
     * Stops looking for crashes.  Does not need to be called in normal operations, but may be
     * useful for testing.
     */
    public void stop() {
        context.unregisterReceiver(receiver);
        Timber.d( "stopped listening for BluetoothAdapter events");
        saveState();
    }

    /**
     * Enable debug logging.  By default no debug lines are logged.
     * @deprecated Since the default logger used by the android-beacon-library only logs warnings and
     * above, this method is no logger used. To log debug messages use the
     * {@link org.altbeacon.beacon.logging.Loggers#verboseLogger()}
     * @see org.altbeacon.beacon.logging.LogManager
     * @see org.altbeacon.beacon.logging.Loggers
     */
    @Deprecated
    public void enableDebug() {

    }

    /**
     * Disable debug logging.
     * @deprecated Since the default logger used by the android-beacon-library only logs warnings and
     * above, this method is no logger used. To log debug messages use the
     * {@link org.altbeacon.beacon.logging.Loggers#verboseLogger()}
     * @see org.altbeacon.beacon.logging.LogManager
     * @see org.altbeacon.beacon.logging.Loggers
     */
    @Deprecated
    public void disableDebug() { }

    /**
     * Call this method from your BluetoothAdapter.LeScanCallback method.
     * Doing so is optional, but if you do, this class will be able to count the number of
     * distinct Bluetooth devices scanned, and prevent crashes before they happen.
     *
     * This works very well if the app containing this class is the only one running bluetooth
     * LE scans on the device, or it is constantly doing scans (e.g. is in the foreground for
     * extended periods of time.)
     *
     * This will not work well if the application using this class is only scanning periodically
     * (e.g. when in the background to save battery) and another application is also scanning on
     * the same device, because this class will only get the counts from this application.
     *
     * Future augmentation of this class may improve this by somehow centralizing the list of
     * unique scanned devices.
     *
     * @param device
     */
    @TargetApi(18)
    public void notifyScannedDevice(BluetoothDevice device, BluetoothAdapter.LeScanCallback scanner) {
        int oldSize, newSize;

        oldSize = distinctBluetoothAddresses.size();

        synchronized(distinctBluetoothAddresses) {
            distinctBluetoothAddresses.add(device.getAddress());
        }

        newSize = distinctBluetoothAddresses.size();
        if (oldSize != newSize && newSize % 100 == 0) {
            Timber.d( "Distinct Bluetooth devices seen: %s", distinctBluetoothAddresses.size());
        }
        if (distinctBluetoothAddresses.size()  > getCrashRiskDeviceCount()) {
            if (PREEMPTIVE_ACTION_ENABLED && !recoveryInProgress) {
                Timber.w( "Large number of Bluetooth devices detected: %s Proactively attempting to clear out address list to prevent a crash",
                        distinctBluetoothAddresses.size());
                Timber.w( "Stopping LE Scan");
                //BluetoothAdapter.getDefaultAdapter().stopLeScan(scanner);
                startRecovery();
                processStateChange();
            }
        }
    }

    public void crashDetected() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            Timber.d( "Ignoring crashes before API 18, because BLE is unsupported.");
            return;
        }
        Timber.w( "BluetoothService crash detected");
        if (distinctBluetoothAddresses.size() > 0) {
            Timber.d( "Distinct Bluetooth devices seen at crash: %s",
                    distinctBluetoothAddresses.size());
        }
        lastBluetoothCrashDetectionTime = SystemClock.elapsedRealtime();
        detectedCrashCount++;

        if (recoveryInProgress) {
            Timber.d( "Ignoring Bluetooth crash because recovery is already in progress.");
        }
        else {
            startRecovery();
        }
        processStateChange();

    }

    public long getLastBluetoothCrashDetectionTime() {
        return lastBluetoothCrashDetectionTime;
    }
    public int getDetectedCrashCount() {
        return detectedCrashCount;
    }
    public int getRecoveryAttemptCount() {
        return recoveryAttemptCount;
    }
    public boolean isLastRecoverySucceeded() {
        return lastRecoverySucceeded;
    }
    public boolean isRecoveryInProgress() { return recoveryInProgress; }

    public interface UpdateNotifier {
        void dataUpdated();
    }

    public void setUpdateNotifier(UpdateNotifier updateNotifier) {
        this.updateNotifier = updateNotifier;
    }

    /**
     Used to force a recovery operation
     */
    public void forceFlush() {
        startRecovery();
        processStateChange();
    }

    private int getCrashRiskDeviceCount() {
        // 1990 distinct devices tracked by Bluedroid will cause a crash.  But we don't know how many
        // devices Bluedroid is tracking, we only know how many we have seen, which will be smaller
        // than the number tracked by Bluedroid because the number we track does not include its
        // initial state.  We therefore assume that there are some devices being tracked by Bluedroid
        // after a recovery operation or on startup
        return BLUEDROID_MAX_BLUETOOTH_MAC_COUNT-BLUEDROID_POST_DISCOVERY_ESTIMATED_BLUETOOTH_MAC_COUNT;
    }

    private void processStateChange() {
        if (updateNotifier != null) {
            updateNotifier.dataUpdated();
        }
        if (SystemClock.elapsedRealtime() - lastStateSaveTime > MIN_TIME_BETWEEN_STATE_SAVES_MILLIS) {
            saveState();
        }
    }

    @SuppressLint("MissingPermission")
    @TargetApi(17)
    private void startRecovery() {
        // The discovery operation will start by clearing out the Bluetooth mac list to only the 256
        // most recently seen BLE mac addresses.
        recoveryAttemptCount++;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Timber.d( "about to check if discovery is active");
        if (!adapter.isDiscovering()) {
            Timber.w( "Recovery attempt started");
            recoveryInProgress = true;
            discoveryStartConfirmed = false;
            Timber.d( "about to command discovery");
            if (!adapter.startDiscovery()) {
                Timber.w( "Can't start discovery.  Is Bluetooth turned on?");
            }
            Timber.d( "startDiscovery commanded.  isDiscovering()=%s", adapter.isDiscovering());
            // We don't actually need to do a discovery -- we just need to kick one off so the
            // mac list will be pared back to 256.  Because discovery is an expensive operation in
            // terms of battery, we will cancel it.
            if (TIME_TO_LET_DISCOVERY_RUN_MILLIS > 0 ) {
                Timber.d( "We will be cancelling this discovery in %s milliseconds.", TIME_TO_LET_DISCOVERY_RUN_MILLIS);
                cancelDiscovery();
            }
            else {
                Timber.d( "We will let this discovery run its course.");
            }
        }
        else {
            Timber.w( "Already discovering.  Recovery attempt abandoned.");
        }

    }
    private void finishRecovery() {
        Timber.w( "Recovery attempt finished");
        synchronized(distinctBluetoothAddresses) {
            distinctBluetoothAddresses.clear();
        }
        recoveryInProgress = false;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                if (recoveryInProgress) {
                    Timber.d( "Bluetooth discovery finished");
                    finishRecovery();
                }
                else {
                    Timber.d( "Bluetooth discovery finished (external)");
                }
            }
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                if (recoveryInProgress) {
                    discoveryStartConfirmed = true;
                    Timber.d( "Bluetooth discovery started");
                }
                else {
                    Timber.d( "Bluetooth discovery started (external)");
                }
            }

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.ERROR:
                        Timber.d( "Bluetooth state is ERROR");
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Timber.d( "Bluetooth state is OFF");
                        lastBluetoothOffTime = SystemClock.elapsedRealtime();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Timber.d( "Bluetooth state is ON");
                        Timber.d( "Bluetooth was turned off for %s milliseconds", lastBluetoothTurningOnTime - lastBluetoothOffTime);
                        if (lastBluetoothTurningOnTime - lastBluetoothOffTime < SUSPICIOUSLY_SHORT_BLUETOOTH_OFF_INTERVAL_MILLIS) {
                            crashDetected();
                        }
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        lastBluetoothTurningOnTime = SystemClock.elapsedRealtime();
                        Timber.d( "Bluetooth state is TURNING_ON");
                        break;
                }
            }
        }
    };


    private void saveState() {
        FileOutputStream outputStream;
        OutputStreamWriter writer = null;
        lastStateSaveTime = SystemClock.elapsedRealtime();

        try {
            outputStream = context.openFileOutput(DISTINCT_BLUETOOTH_ADDRESSES_FILE, Context.MODE_PRIVATE);
            writer = new OutputStreamWriter(outputStream);
            writer.write(lastBluetoothCrashDetectionTime+"\n");
            writer.write(detectedCrashCount+"\n");
            writer.write(recoveryAttemptCount+"\n");
            writer.write(lastRecoverySucceeded ? "1\n" : "0\n");
            synchronized (distinctBluetoothAddresses) {
                for (String mac : distinctBluetoothAddresses) {
                    writer.write(mac);
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            Timber.w( "Can't write macs to %s", DISTINCT_BLUETOOTH_ADDRESSES_FILE);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e1) { }
            }
        }
        Timber.d( "Wrote %s Bluetooth addresses", distinctBluetoothAddresses.size());

    }

    private void loadState() {
        FileInputStream inputStream;
        BufferedReader reader = null;

        try {
            inputStream = context.openFileInput(DISTINCT_BLUETOOTH_ADDRESSES_FILE);
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            line = reader.readLine();
            if (line != null) {
                lastBluetoothCrashDetectionTime = Long.parseLong(line);
            }
            line = reader.readLine();
            if (line != null) {
                detectedCrashCount = Integer.parseInt(line);
            }
            line = reader.readLine();
            if (line != null) {
                recoveryAttemptCount = Integer.parseInt(line);
            }
            line = reader.readLine();
            if (line != null) {
                lastRecoverySucceeded = false;
                if (line.equals("1")) {
                    lastRecoverySucceeded = true;
                }
            }

            String mac;
            while ((mac = reader.readLine()) != null) {
                distinctBluetoothAddresses.add(mac);
            }

        } catch (IOException e) {
            Timber.w( "Can't read macs from %s", DISTINCT_BLUETOOTH_ADDRESSES_FILE);
        } catch (NumberFormatException e) {
            Timber.w( "Can't parse file %s", DISTINCT_BLUETOOTH_ADDRESSES_FILE);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) { }
            }
        }
        Timber.d( "Read %s Bluetooth addresses", distinctBluetoothAddresses.size());
    }

    @SuppressLint("MissingPermission")
    private void cancelDiscovery() {
        try {
            Thread.sleep(TIME_TO_LET_DISCOVERY_RUN_MILLIS);
            if (!discoveryStartConfirmed) {
                Timber.w( "BluetoothAdapter.ACTION_DISCOVERY_STARTED never received.  Recovery may fail.");
            }

            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter.isDiscovering()) {
                Timber.d( "Cancelling discovery");
                adapter.cancelDiscovery();
            }
            else {
                Timber.d( "Discovery not running.  Won't cancel it");
            }
        } catch (InterruptedException e) {
            Timber.d( "DiscoveryCanceller sleep interrupted.");
        }
    }
}