package wtf.kl.locshare;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toolbar;

import com.google.android.gms.location.LocationRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String REQUEST_LOCATION_PERMISSIONS = "wtf.kl.locshare.REQUEST_LOCATION_PERMISSIONS";
    private static final int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_FINE_LOCATION:
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(this, LocationService.class);
                    intent.setAction(LocationService.START_LOCATION_SERVICE);
                    startService(intent);
                } else {
                    SharedPreferences.Editor edit = sp.edit();
                    edit.putBoolean("share_location", false);
                    edit.apply();
                }
        }
    }

    private void storeUsers() {
        File file = new File(getFilesDir(), "users");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file, false);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        JSONObject obj;
        try {
            obj = UserStore.toJSON();
        } catch (JSONException e) {
            e.printStackTrace();
            try { fos.close(); } catch (IOException x) {}
            return;
        }

        byte[] b = obj.toString().getBytes(StandardCharsets.US_ASCII);

        try {
            fos.write(b);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadUsers() {
        File file = new File(getFilesDir(), "users");
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return;
        }

        byte[] data = new byte[(int)file.length()];
        try {
            fis.read(data);
            fis.close();
        } catch (IOException e) {
            return;
        }

        JSONObject obj;
        try {
            obj = new JSONObject(new String(data, StandardCharsets.US_ASCII));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        try {
            UserStore.fromJSON(obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "share_location":
                Intent intent = new Intent(this, LocationService.class);
                intent.setAction(LocationService.START_LOCATION_SERVICE);
                startService(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        sp.registerOnSharedPreferenceChangeListener(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            startActivity(intent);
        });

        if (UserStore.isEmpty())
            loadUsers();

        Intent intent = getIntent();
        if (intent.getAction().equals(REQUEST_LOCATION_PERMISSIONS)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }

        Intent locIntent = new Intent(this, LocationService.class);
        locIntent.setAction(LocationService.START_LOCATION_SERVICE);
        startService(locIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        storeUsers();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
}
