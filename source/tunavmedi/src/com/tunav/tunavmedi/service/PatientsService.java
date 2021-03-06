
package com.tunav.tunavmedi.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.tunav.tunavmedi.R;
import com.tunav.tunavmedi.TunavMedi;
import com.tunav.tunavmedi.broadcastreceiver.BatteryReceiver;
import com.tunav.tunavmedi.broadcastreceiver.ChargingReceiver;
import com.tunav.tunavmedi.broadcastreceiver.NetworkReceiver;
import com.tunav.tunavmedi.dal.datatype.Patient;
import com.tunav.tunavmedi.dal.sqlite.helper.PatientsHelper;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PatientsService extends Service implements
        OnSharedPreferenceChangeListener,
        LocationListener {

    public interface PatientsListener {

        public void updateDataSet(ArrayList<Patient> newPatients, Location location);
    }

    public class SelfBinder extends Binder {
        public PatientsService getService() {
            return PatientsService.this;
        }
    }

    public static final String tag = "PatientsService";

    private OnSharedPreferenceChangeListener devListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getResources().getString(R.string.spkey_fakelocation))) {
                devUseFakeLocation = sharedPreferences.getBoolean(key, false);
                updateListeners();
            }
        }
    };
    // This is the object that receives interactions from clients. See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new SelfBinder();

    private final Integer corePoolSize = 2;;

    private Observer mHelperObserver = new Observer() {
        @Override
        public void update(Observable observable, Object o) {
            Log.v(tag + ".mHelperObserver", "update()");
            @SuppressWarnings("unchecked")
            final ArrayList<Patient> oPatient = (ArrayList<Patient>) o;
            mScheduler.submit(new Runnable() {
                @Override
                public void run() {
                    syncPatients(oPatient);
                }
            });
        }
    };

    private volatile ArrayList<Patient> mPatientsCache = new ArrayList<Patient>();
    private volatile ArrayList<PatientsListener> mPatientsListeners = new ArrayList<PatientsService.PatientsListener>();
    private volatile Location mLocationCache;
    private boolean devUseFakeLocation;
    private PatientsHelper mHelper = null;
    private ScheduledExecutorService mScheduler = null;
    private Future<?> mPatientsFuture = null;
    private static final long MIN_TIME = 1000 * 60 * 15;
    private static final float MIN_DIST = 5;
    private boolean isLogged;
    private boolean batteryOk;
    private boolean powerConnected;
    private boolean isConnected;
    private final int LOCATION_BATTERY_THRESHOLD = 50;
    private final long LOCATION_TOO_OLD = TimeUnit.HOURS.toNanos(1);

    public void addPatientsListener(PatientsListener listener) {
        if (listener != null && !mPatientsListeners.contains(listener)) {
            mPatientsListeners.add(listener);
        }
        updateListeners();
    }

    private int getPowerCriteria() {
        if (BatteryReceiver.getBatteryLevel(this) > LOCATION_BATTERY_THRESHOLD) {
            return Criteria.POWER_HIGH;
        } else {
            return Criteria.POWER_LOW;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(tag, "onBind()");
        return mBinder;
    }

    private void onConfigure() {
        Log.v(tag, "onConfigure()");

        if (TunavMedi.debugLocation() || batteryOk && !powerConnected) {
            startLocationPolling(Criteria.ACCURACY_FINE, getPowerCriteria(), MIN_TIME, MIN_DIST);
        } else {
            stopLocationPolling();
        }

        if (TunavMedi.debugNotification() || isLogged && batteryOk && isConnected) {
            startPatientNotification();
        } else {
            stopPatientsNotification();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(tag, "onCreate()");

        mScheduler = Executors.newScheduledThreadPool(corePoolSize);

        mHelper = new PatientsHelper(this);
        mHelper.addObserver(mHelperObserver);
        syncPatients(mHelper.pullPatients());

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location LastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        Log.i(tag, "Last known location elapsed time: " + LastKnown.getElapsedRealtimeNanos());
        Log.i(tag, "Current elapsed time: " + SystemClock.elapsedRealtimeNanos());
        Log.i(tag,
                "Difference: "
                        + (SystemClock.elapsedRealtimeNanos() - LastKnown.getElapsedRealtimeNanos()));
        Log.i(tag, "Max difference : " + LOCATION_TOO_OLD);

        if (SystemClock.elapsedRealtimeNanos() - LastKnown.getElapsedRealtimeNanos() <= LOCATION_TOO_OLD) {
            Log.i(tag, "Last Known Location Accepted!");
            mLocationCache = new Location(LastKnown);
        }

        SharedPreferences spUser = getSharedPreferences(getResources()
                .getString(R.string.sp_user), MODE_PRIVATE);

        isLogged = spUser.getBoolean(getResources()
                .getString(R.string.spkey_sort_by_location), false);
        spUser.registerOnSharedPreferenceChangeListener(this);

        SharedPreferences spStatus = getSharedPreferences(
                getResources().getString(R.string.sp_status), MODE_PRIVATE);
        spStatus.registerOnSharedPreferenceChangeListener(this);

        SharedPreferences spDev = getSharedPreferences(getResources().getString(R.string.sp_dev),
                MODE_PRIVATE);
        spDev.registerOnSharedPreferenceChangeListener(devListener);

        devUseFakeLocation = spDev.getBoolean(getResources()
                .getString(R.string.spkey_fakelocation), false);

        batteryOk = BatteryReceiver.getBatteryOk(this);
        powerConnected = ChargingReceiver.isCharging(this);
        isConnected = NetworkReceiver.isConnected(this);

        onConfigure();
    }

    @Override
    public void onDestroy() {
        Log.v(tag, "onDestroy()");

        SharedPreferences spUser = getSharedPreferences(getResources()
                .getString(R.string.sp_user), MODE_PRIVATE);
        spUser.unregisterOnSharedPreferenceChangeListener(this);

        SharedPreferences spStatus = getSharedPreferences(
                getResources().getString(R.string.sp_status), MODE_PRIVATE);
        spStatus.unregisterOnSharedPreferenceChangeListener(this);

        mHelper.deleteObserver(mHelperObserver);

        stopLocationPolling();
        stopPatientsNotification();

        mScheduler.shutdownNow();

        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.v(tag, "onLocationChanged()");
        Log.i(tag, location.toString());

        mLocationCache = new Location(location);
        updateListeners();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.v(tag, "onProviderDisabled()");
        onConfigure();
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.v(tag, "onProviderEnabled()");
        onConfigure();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(tag, "onSharedPreferenceChanged()");
        if (key.equals(getResources().getString(R.string.spkey_is_logged))) {
            isLogged = sharedPreferences.getBoolean(key, false);
        } else if (key.equals(getResources().getString(R.string.spkey_battery_okey))) {
            batteryOk = sharedPreferences.getBoolean(key, batteryOk);
        } else if (key.equals(getResources().getString(R.string.spkey_is_connected))) {
            isConnected = sharedPreferences.getBoolean(key, isConnected);
        } else if (key.equals(getResources().getString(R.string.spkey_power_connected))) {
            powerConnected = sharedPreferences.getBoolean(key, powerConnected);
        }
        onConfigure();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("PresenterService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.v(tag, "onStatusChanged()");
        Log.i(tag, provider + "status" + status);
        // TODO Auto-generated method stub

    }

    public void removePatientsListener(PatientsListener listener) {
        mPatientsListeners.remove(listener);
    }

    private void startLocationPolling(int accuracy, int power, long minTime, float minDistance) {
        Log.v(tag, "startLocationPolling()");

        stopLocationPolling();

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(accuracy);
        criteria.setPowerRequirement(power);
        criteria.setAltitudeRequired(true);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setCostAllowed(false);

        String bestProvider = locationManager.getBestProvider(criteria,
                true);
        locationManager.requestLocationUpdates(bestProvider, minTime, minDistance, this);
        Log.i(tag, "Location Updates Requested!" + "\nProvider: " + bestProvider + "\nminTime: "
                + minTime + "\n minDistance: " + minDistance);
    }

    private void startPatientNotification() {
        Log.v(tag, "startPatientNotification()");

        if (mPatientsFuture != null) {
            mPatientsFuture.cancel(true);
            mPatientsFuture = null;
        }

        mPatientsFuture = mScheduler.submit(mHelper.getNotifyTask());
    }

    private void stopLocationPolling() {
        Log.v(tag, "stopLocationPolling()");

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(this);
    }

    private void stopPatientsNotification() {
        Log.v(tag, "stopPatientsNotification()");
        if (mPatientsFuture != null) {
            mPatientsFuture.cancel(true);
            mPatientsFuture = null;
        }
    }

    public void syncPatient(Patient updatedPatient) {
        Log.v(tag, "syncPatient()");
        for (Patient patient : mPatientsCache) {
            if (patient.getId() == updatedPatient.getId()) {
                int index = mPatientsCache.indexOf(patient);
                mPatientsCache.set(index, updatedPatient);
                break;
            }
        }
        if (mHelper.pushPatients(mPatientsCache) > 0) {
            updateListeners();
        } else {
            Toast.makeText(this, "Sync Problems!", Toast.LENGTH_LONG).show();
        }
    }

    public void syncPatients(ArrayList<Patient> newPatients) {
        Log.d(tag, "syncPatients()");
        mPatientsCache.clear();
        mPatientsCache.addAll(newPatients);
        updateListeners();
    }

    public void updateListeners() {
        Log.v(tag, "updateListeners()");
        for (PatientsListener listener : mPatientsListeners) {
            listener.updateDataSet(mPatientsCache, devUseFakeLocation ? TunavMedi.devFakeLocation
                    : mLocationCache);
        }
    }
}
