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

import java.io.File;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String REQUEST_LOCATION_PERMISSIONS = "wtf.kl.locshare.REQUEST_LOCATION_PERMISSIONS";
    private static final int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_FINE_LOCATION:
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(this, LocationPublisher.class);
                    intent.setAction(LocationPublisher.START_LOCATION_SERVICE);
                    startService(intent);
                } else {
                    SharedPreferences.Editor edit = sp.edit();
                    edit.putBoolean("share_location", false);
                    edit.apply();
                }
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "share_location":
                Intent intent = new Intent(this, LocationPublisher.class);
                intent.setAction(LocationPublisher.START_LOCATION_SERVICE);
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

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.main_fab);
        fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            startActivity(intent);
        });

        if (UserStore.isEmpty()) {
            try {
                UserStore.readFromFile(new File(getFilesDir(), "users"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent intent = getIntent();
        if (intent.getAction().equals(REQUEST_LOCATION_PERMISSIONS)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }

        Intent locIntent = new Intent(this, LocationPublisher.class);
        locIntent.setAction(LocationPublisher.START_LOCATION_SERVICE);
        startService(locIntent);
    }

    @Override
    protected void onPause() {
        try {
            UserStore.writeToFile(new File(getFilesDir(), "users"));
        } catch (Exception e) {
            e.printStackTrace();
        }

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
