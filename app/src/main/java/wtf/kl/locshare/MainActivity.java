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
import android.widget.Toast;
import android.widget.Toolbar;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String REQUEST_LOCATION_PERMISSIONS = "wtf.kl.locshare.REQUEST_LOCATION_PERMISSIONS";
    private static final int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_FINE_LOCATION:
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(this, PublisherService.class);
                    intent.setAction(PublisherService.START_LOCATION_SERVICE);
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
                Intent intent = new Intent(this, PublisherService.class);
                intent.setAction(PublisherService.START_LOCATION_SERVICE);
                startService(intent);
                break;
            case "server_url":
                Client.setURL(sharedPreferences.getString(key, ""));
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Storage.createInstance(this);
        try {
            Storage.getInstance().load();
        } catch (Exception e) {
            e.printStackTrace();
        }

        setContentView(R.layout.activity_main);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        sp.registerOnSharedPreferenceChangeListener(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.main_fab);
        fab.setOnClickListener(view -> {
            AddUserAction.build(this);
        });

        Client.setToken(sp.getString("auth_token", ""));
        Client.setUsername(sp.getString("auth_username", ""));
        Client.setURL(sp.getString("server_url", ""));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null && intent.getAction().equals(REQUEST_LOCATION_PERMISSIONS)) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            }
            setIntent(null);
            return;
        }

        Intent locIntent = new Intent(this, PublisherService.class);
        locIntent.setAction(PublisherService.START_LOCATION_SERVICE);
        startService(locIntent);
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
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_login:
                intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                break;
            case R.id.action_create:
                intent = new Intent(this, NewAccountActivity.class);
                startActivity(intent);
                break;
            case R.id.action_run_test:
                try {
                    new Tester().run();
                    Toast success = Toast.makeText(this, "Test completed successfully",
                            Toast.LENGTH_SHORT);
                    success.show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast failure = Toast.makeText(this, "Tests failed",
                            Toast.LENGTH_SHORT);
                    failure.show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
