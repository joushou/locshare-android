package wtf.kl.locshare;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

public class LocationPublisher extends Service implements GoogleApiClient.ConnectionCallbacks {
    private static final String TAG = "locshare/LocSer";
    public static final String START_LOCATION_SERVICE = "wtf.kl.locshare.START_LOCATION_SERVICE";
    public static final String NETWORK_NOTIFICATION = "wtf.kl.locshare.NETWORK_NOTIFICATION";

    private ArrayList<Intent> startReason = new ArrayList<>();

    private PendingIntent pendingLocationIntent = null;
    private PendingIntent pendingNetworkIntent = null;
    private GoogleApiClient googleApiClient = null;

    private class PublisherArg {
        final String host;
        final int port;
        final boolean movement;
        final Location location;

        PublisherArg(String host, int port, boolean movement, Location location) {
            this.host = host;
            this.port = port;
            this.movement = movement;
            this.location = location;
        }
    }

    private class PublisherTask extends AsyncTask<PublisherArg, Void, Boolean> {
        @Override
        public Boolean doInBackground(PublisherArg ...params) {
            for (PublisherArg arg : params) {
                Socket socket = null;
                BufferedWriter os = null;
                try {
                    socket = new Socket(arg.host, arg.port);
                    os = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    Set<String> keys = UserStore.getUserKeys();

                    if (!arg.movement) {
                        arg.location.setSpeed(0);
                        arg.location.setBearing(0);
                    }
                    byte[] msg = LocationCodec.encode(arg.location);

                    for (String key : keys) {
                        User user = UserStore.getUser(key);
                        if (user.remotePubKey == null || user.remotePubKey.length != 32)
                            continue;

                        byte[] p = CryptoManager.encryptWithCurve25519PublicKey(msg, user.remotePubKey);
                        String data = Base64.encodeToString(p, Base64.NO_WRAP | Base64.URL_SAFE);
                        os.write(String.format("pub %s %s\n", user.remoteAsBase64(), data));
                    }

                    os.flush();
                    os.close();
                    socket.close();

                    os = null;
                    socket = null;

                } catch (IOException e) {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException x) {
                            // PASS
                        }
                    }
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException x) {
                            // PASS
                        }
                    }

                    return false;
                }
            }

            return true;
        }

        @Override
        public void onPostExecute(Boolean success) {
            if (!success && !isInternetAvailable()) {
                waitForNetwork();
            }
        }
    }

    public void registerLocationWatcher() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean shareLocation = sp.getBoolean("share_location", true);

        if (shareLocation && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Get MainActivity to get us those precious permissions!
            Intent dialogIntent = new Intent(this, MainActivity.class);
            dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            dialogIntent.setAction(MainActivity.REQUEST_LOCATION_PERMISSIONS);
            startActivity(dialogIntent);
            return;
        }

        if (pendingLocationIntent != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, pendingLocationIntent);
            pendingLocationIntent.cancel();
            pendingLocationIntent = null;
        }

        if (!shareLocation)
            return;

        LocationRequest locationRequest = LocationRequest.create();

        int priority = sp.getInt("precision", LocationRequest.PRIORITY_NO_POWER);
        int maxInterval = sp.getInt("max_update_interval", 60000);
        int interval = sp.getInt("update_interval", 900000);

        locationRequest
                .setPriority(priority)
                .setFastestInterval(maxInterval)
                .setInterval(interval)
                .setSmallestDisplacement(25);

        Intent intent = new Intent(this, LocationPublisher.class);
        pendingLocationIntent = PendingIntent.getService(this, 0, intent, 0);

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, pendingLocationIntent);

        publish(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));

    }

    private void waitForNetwork() {
        if (pendingLocationIntent != null) {

            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, pendingLocationIntent);
            pendingLocationIntent.cancel();
            pendingLocationIntent = null;
        }

        Log.v(TAG, "network error: temporarily suspending service");

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

        Intent intent = new Intent(this, LocationPublisher.class);
        intent.setAction(NETWORK_NOTIFICATION);
        pendingNetworkIntent = PendingIntent.getService(this, 0, intent, 0);

        cm.registerNetworkCallback(networkRequest, pendingNetworkIntent);

        stopSelf();
    }

    private void networkNotification() {
        if (pendingNetworkIntent == null) {
            if (isInternetAvailable())
                registerLocationWatcher();
            return;

        }

        Log.v(TAG, "network callback: resuming service");

        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.unregisterNetworkCallback(pendingNetworkIntent);
        pendingNetworkIntent.cancel();
        pendingNetworkIntent = null;

        registerLocationWatcher();
    }

    private void publish(Location location) {
        if (location == null)
            return;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        PublisherArg publisherArg = new PublisherArg(
                sp.getString("server_address", ""),
                sp.getInt("server_port", 0),
                sp.getBoolean("share_movement", true),
                location
        );

        new PublisherTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, publisherArg);
    }

    private static boolean isNetworkNotification(Intent intent) {
        String action = intent.getAction();
        return action != null && action.equals(NETWORK_NOTIFICATION);
    }

    private static boolean isStartRequest(Intent intent) {
        String action = intent.getAction();
        return action != null && action.equals(START_LOCATION_SERVICE);
    }

    public boolean isInternetAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) (getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void onConnected(Bundle connectionHint) {
        if (startReason != null) {
            ArrayList<Intent> sr = startReason;

            startReason = new ArrayList<>();
            for (Intent intent : sr) {
                onStartCommand(intent, 0, 0);
            }
        }
    }

    public void onConnectionSuspended(int cause) {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (googleApiClient == null || !googleApiClient.isConnected()) {
            startReason.add(intent);

            if (googleApiClient != null) {
                if (googleApiClient.isConnecting())
                    return START_NOT_STICKY;
            } else {

                googleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .build();
            }

            googleApiClient.connect();

            return START_NOT_STICKY;
        }

        if (!googleApiClient.isConnected())
            return START_NOT_STICKY;

        if (intent == null)
            return START_NOT_STICKY;

        if (LocationResult.hasResult(intent)) {
            LocationResult locationResult = LocationResult.extractResult(intent);
            publish(locationResult.getLastLocation());
        } else if (isNetworkNotification(intent)) {
            networkNotification();
        } else if (isStartRequest(intent)) {
            registerLocationWatcher();
        }

        return START_NOT_STICKY;

    }

    @Override
    public void onDestroy() {
        if (googleApiClient != null) {
            googleApiClient.disconnect();
            googleApiClient = null;
        }

        if (startReason != null && startReason.size() > 0) {
            startReason.clear();
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}