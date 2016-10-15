package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toolbar;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks {
    private GoogleApiClient googleApiClient = null;
    private User user = null;
    private Location location = null;

    private class GeocoderTask extends AsyncTask<Location, Void, Address> {
        private final Context context;

        public GeocoderTask(Context context) {
            this.context = context;
        }
        protected Address doInBackground(Location... params) {
            if (params.length != 1) return null;
            Geocoder gcd = new Geocoder(context, Locale.getDefault());

            try {
                List<Address> addresses = gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses.size() == 0)
                    return null;

                return addresses.get(0);
            } catch (IOException e) {
                return null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        protected void onPostExecute(Address addr) {
            View bottomSheet = findViewById( R.id.map_bottom_sheet );

            if (addr != null) {
                String locationStr = "";
                for (int i = 0; i < addr.getMaxAddressLineIndex(); i++) {
                    locationStr += addr.getAddressLine(i) + "\n";
                }
                locationStr += addr.getCountryName();
                TextView address = (TextView) bottomSheet.findViewById(R.id.map_sheet_address);
                address.setText(locationStr);
                TextView location = (TextView) bottomSheet.findViewById(R.id.map_sheet_locality);
                location.setText(addr.getLocality());
                TextView thoroughfare = (TextView) bottomSheet.findViewById(R.id.map_sheet_thoroughfare);
                thoroughfare.setText(addr.getThoroughfare());
            }

            BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("This action cannot be undone")
                        .setTitle("Delete user?")
                        .setNegativeButton("Cancel", (dialog, id) -> {})
                        .setPositiveButton("Delete", (dialog, id) -> {
                            UserStore.delUser(user);
                            finish();
                        });

                // 3. Get the AlertDialog from create()
                AlertDialog dialog = builder.create();
                dialog.show();
            case R.id.action_edit:
                Intent intent = new Intent(this, EditActivity.class);
                intent.putExtra("uuid", user.uuid);

                startActivity(intent);
        }


        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar ab = getActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        View bottomSheet = findViewById(R.id.map_bottom_sheet);
        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        View peek = bottomSheet.findViewById(R.id.map_sheet_peek);
        peek.post(() -> {
            bottomSheetBehavior.setPeekHeight(peek.getHeight());
        });

        MapFragment mapFragment =
                (MapFragment) getFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        Bundle b = intent.getExtras();
        if (b == null) {
            finish();
            return;
        }

        user = UserStore.getUser(b.getString("uuid"));
        location = user.getLastLocation();

        if (location != null) {
            View bottomSheet = findViewById(R.id.map_bottom_sheet);

            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm dd/MM/yyyy");
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(location.getTime());
            TextView time = (TextView) bottomSheet.findViewById(R.id.map_sheet_time);
            time.setText(formatter.format(calendar.getTime()));

            TextView coordinates = (TextView) bottomSheet.findViewById(R.id.map_sheet_coordinates);
            coordinates.setText(String.format("%.8f, %.8f (±%.2fm)", location.getLatitude(), location.getLongitude(), location.getAccuracy()));

            TextView movement = (TextView) bottomSheet.findViewById(R.id.map_sheet_movement);
            movement.setText(String.format("%.1f km/h\n%.1f degrees", location.getSpeed() / 1000, location.getBearing()));

            new GeocoderTask(getApplicationContext()).execute(location);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(user.name);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();

        googleApiClient.connect();

    }

    private String distanceAsString(double distance) {
        if (Double.isNaN(distance)) {
            return "";
        } else if (distance > 1010000) {
            return String.format("%.2f Mm", distance/1000000);
        } else if (distance > 1010) {
            return String.format("%.2f km", distance/1000);
        } else if (distance > 1.01) {
            return String.format("%.2f m", distance);
        } else {
            return String.format("%.2f mm", distance/1000);
        }
    }

    public void onConnected(Bundle connectionHint) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setExpirationDuration(500);

        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, (loc) -> {
                String distance = distanceAsString(loc.distanceTo(user.getLastLocation()));
                View bottomSheet = findViewById(R.id.map_bottom_sheet);
                TextView d = (TextView)bottomSheet.findViewById(R.id.map_sheet_distance);
                d.setText(distance);
            });
        } catch (SecurityException e) {

        }
    }

    public void onConnectionSuspended(int cause) {}


    @Override
    public void onPause() {
        super.onPause();

        if(googleApiClient != null) {
            googleApiClient.disconnect();
            googleApiClient = null;
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(user.name);

        try {
            map.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            // PASS
        }

        if (location != null) {
            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
            if (location.getAccuracy() > 100) {
                map.addCircle(new CircleOptions()
                        .center(ll)
                        .radius(location.getAccuracy())
                        .strokeColor(0x300000FF)
                        .fillColor(0x200000FF));
            }
            map.addMarker(new MarkerOptions().position(ll).title(user.name));
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 15));
        }

    }
}
