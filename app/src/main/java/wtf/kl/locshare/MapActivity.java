package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static java.lang.Math.abs;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks {
    private GoogleApiClient googleApiClient = null;
    private User user = null;
    private Location location = null;
    private GoogleMap map = null;
    private GeocodeUtil.GeocoderTask geocoderTask = null;
    private BottomSheetBehavior<View> bottomSheetBehavior = null;
    private View bottomSheet = null;
    private final LocationSubscriber locationSubscriber = new LocationSubscriber();
    private boolean mapInPlace = false;


    private void updateSheet() {
        if (location == null)
            return;

        View bottomSheet = findViewById(R.id.map_bottom_sheet);

        DateFormat formatter = SimpleDateFormat.getDateTimeInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(location.getTime());
        TextView time = (TextView) bottomSheet.findViewById(R.id.map_sheet_time);
        time.setText(formatter.format(calendar.getTime()));

        Double latitude = location.getLatitude();
        Double longitude = location.getLongitude();
        TextView coordinates = (TextView) bottomSheet.findViewById(R.id.map_sheet_coordinates);
        coordinates.setText(String.format(Locale.ENGLISH,
                "%.5f°%s %.5f°%s",
                abs(latitude),
                latitude >= 0 ? "N" : "S",
                abs(longitude),
                longitude >= 0 ? "E" : "W"));

        TextView movement = (TextView) bottomSheet.findViewById(R.id.map_sheet_movement);
        movement.setText(String.format(Locale.ENGLISH, "%.1f km/h\n%.1f degrees", location.getSpeed() / 1000, location.getBearing()));

        updateMarkers();

        GeocodeUtil.Request req = new GeocodeUtil.Request(user.uuid, location, new int[]{GeocodeUtil.TWO_LINE_SUMMARY, GeocodeUtil.FULL_ADDRESS});

        geocoderTask = GeocodeUtil.Geocode(this, new GeocodeUtil.Request[]{req}, (resps) -> {
            if (resps.length == 0) return;

            GeocodeUtil.Response resp = resps[0];
            if (!resp.success) return;

            TextView localityView = (TextView) bottomSheet.findViewById(R.id.map_sheet_locality);
            TextView thoroughfareView = (TextView) bottomSheet.findViewById(R.id.map_sheet_thoroughfare);
            TextView addressView = (TextView) bottomSheet.findViewById(R.id.map_sheet_address);

            for (GeocodeUtil.Response.Result result : resp.results) {
                if (result.lines == null) break;
                switch (result.type) {
                    case GeocodeUtil.FULL_ADDRESS:
                        addressView.setText(result.lines[0]);
                        break;
                    case GeocodeUtil.TWO_LINE_SUMMARY:
                        thoroughfareView.setText(result.lines[0]);
                        localityView.setText(result.lines[1]);
                        break;
                }
            }

            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

            geocoderTask = null;
        });
    }

    private void updateMarkers() {
        if (map == null)
            return;

        map.clear();
        location = user.getLastLocation();

        if (location == null)
            return;

        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        if (location.getAccuracy() > 20) {
            map.addCircle(new CircleOptions()
                    .center(ll)
                    .radius(location.getAccuracy())
                    .strokeWidth(4)
                    .fillColor(0x30FFAA00)
                    .strokeColor(0x70FFAA00));
        }

        map.addMarker(new MarkerOptions().position(ll).title(user.name));
        if (!mapInPlace) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 15));
            mapInPlace = true;
        } else {
           map.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15));
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
                break;

            case R.id.action_edit:
                Intent intent = new Intent(this, EditActivity.class);
                intent.putExtra("uuid", user.uuid);

                startActivity(intent);
                break;
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
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        bottomSheet = findViewById(R.id.map_bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        View peek = bottomSheet.findViewById(R.id.map_sheet_peek);
        peek.post(() -> bottomSheetBehavior.setPeekHeight(peek.getHeight()));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event){
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            switch (bottomSheetBehavior.getState()) {
                case BottomSheetBehavior.STATE_EXPANDED:
                    Rect outRect = new Rect();
                    bottomSheet.getGlobalVisibleRect(outRect);

                    if(!outRect.contains((int)event.getRawX(), (int)event.getRawY()))
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    break;
            }
        }

        return super.dispatchTouchEvent(event);
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

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(user.name);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();

        googleApiClient.connect();

        updateSheet();

        mapInPlace = false;
        if (map == null) {
            MapFragment mapFragment =
                    (MapFragment) getFragmentManager().findFragmentById(R.id.map_fragment);
            mapFragment.getMapAsync(this);
        } else {
            updateMarkers();
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        locationSubscriber.start(
                sp.getString("server_address", ""),
                sp.getInt("server_port", 0),
                new String[]{user.uuid},
                (uuid) -> updateSheet());

    }

    private String distanceAsString(double distance) {
        if (Double.isNaN(distance)) {
            return "";
        } else if (distance > 999999) {
            return String.format(Locale.ENGLISH, "%.1f Mm", distance/1000000);
        } else if (distance > 999) {
            return String.format(Locale.ENGLISH, "%.1f km", distance/1000);
        } else if (distance > 0.999) {
            return String.format(Locale.ENGLISH, "%.1f m", distance);
        } else {
            return String.format(Locale.ENGLISH, "%.2f mm", distance/1000);
        }
    }

    public void onConnected(Bundle connectionHint) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setExpirationDuration(500);

        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, (loc) -> {
                if (user.getLastLocation() == null)
                    return;

                String distance = distanceAsString(loc.distanceTo(user.getLastLocation()));
                View bottomSheet = findViewById(R.id.map_bottom_sheet);
                TextView d = (TextView) bottomSheet.findViewById(R.id.map_sheet_distance);
                d.setText(distance);
            });
        } catch (SecurityException e) {
            // PASS
        }
    }

    public void onConnectionSuspended(int cause) {}


    @Override
    public void onPause() {
        super.onPause();

        locationSubscriber.stop();

        if (geocoderTask != null) {
            geocoderTask.cancel(true);
            geocoderTask = null;
        }
        if(googleApiClient != null) {
            googleApiClient.disconnect();
            googleApiClient = null;
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        map.clear();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(user.name);

        try {
            map.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            // PASS
        }
        this.map = map;

        updateMarkers();
    }
}
