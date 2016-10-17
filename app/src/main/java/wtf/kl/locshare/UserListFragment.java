package wtf.kl.locshare;

import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.abs;

class UserListAdapter extends ArrayAdapter<UserListItem> {
    private static class ViewHolder {
        TextView nameView;
        TextView locView;
        TextView distView;
        TextView timeView;
    }
    private final Context context;
    final ArrayList<UserListItem> values;

    UserListAdapter(Context context, ArrayList<UserListItem> values) {
        super(context, R.layout.main_rowlayout, values);
        this.context = context;
        this.values = values;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        ViewHolder viewHolder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.main_rowlayout, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.nameView = (TextView) convertView.findViewById(R.id.userlist_name);
            viewHolder.locView = (TextView) convertView.findViewById(R.id.userlist_location);
            viewHolder.distView = (TextView) convertView.findViewById(R.id.userlist_distance);
            viewHolder.timeView = (TextView) convertView.findViewById(R.id.userlist_time);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        UserListItem u = values.get(position);
        if (u != null) {
            viewHolder.nameView.setText(u.name);
            viewHolder.locView.setText(u.getLocationString());
            viewHolder.distView.setText(u.getDistanceString());
            viewHolder.timeView.setText(u.getTimeString());
        }

        return convertView;
    }
}

class UserListItem {
    final String name;
    final String uuid;
    private Location location;

    private String locationString;
    private double distance;

    UserListItem(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    boolean updateLocation(Location loc, double distance) {
        boolean needUpdate = locationString == null ||
                location == null ||
                location.getAccuracy() <= loc.getAccuracy() * 1.5 ||
                location.distanceTo(loc) < 10;

        this.location = loc;
        this.distance = distance;

        return needUpdate;
    }

    void updateLocationString(String locationString) {
        this.locationString = locationString;
    }

    String getLocationString() {
        if (location == null)
            return "Location not known";

        if (locationString != null && !locationString.isEmpty())
            return locationString;

        Double latitude = location.getLatitude();
        Double longitude = location.getLongitude();

        return String.format(Locale.ENGLISH,
                "%.5f°%s %.5f°%s",
                abs(latitude),
                latitude >= 0 ? "N" : "S",
                abs(longitude),
                longitude >= 0 ? "E" : "W");
    }

    String getDistanceString() {
        if (Double.isNaN(distance)) {
            return "";
        } else if (distance > 999999) {
            return String.format(Locale.ENGLISH, "%.1f Mm", distance/1000000);
        } else if (distance > 999) {
            return String.format(Locale.ENGLISH, "%.1f km", distance/1000);
        } else if (distance > 0.999) {
            return String.format(Locale.ENGLISH, "%.1f m", distance);
        } else {
            return String.format(Locale.ENGLISH, "%.1f mm", distance/1000);
        }
    }

    String getTimeString() {
        if (location == null)
            return "";

        Calendar calendar = Calendar.getInstance();
        long time = calendar.getTimeInMillis();

        if ((time - location.getTime()) < 60000) {
            return "Now";
        }

        return DateUtils.getRelativeTimeSpanString(location.getTime(), time, DateUtils.MINUTE_IN_MILLIS).toString();
    }
}

/**
 * A placeholder fragment containing a simple view.
 */
public class UserListFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener, GoogleApiClient.ConnectionCallbacks {
    SwipeRefreshLayout swipeRefreshLayout;
    private GoogleApiClient googleApiClient = null;
    private UserListAdapter adapter = null;
    private GeocodeUtil.GeocoderTask geocoderTask = null;
    private long listVersion = 0;
    private Location location = null;
    private final LocationSubscriber locationSubscriber = new LocationSubscriber();

    public UserListFragment() {}

    private void updateTimes() {
        adapter.notifyDataSetChanged();
        View view = getView();
        if (view != null)
            view.postDelayed(this::updateTimes, 60000);
    }

    public void onRefresh() {
        if (adapter == null || geocoderTask != null) return;

        if (googleApiClient != null && googleApiClient.isConnected()) {
            try {
                location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            } catch (SecurityException e) {
                // PASS
            }
        }

        Map<String, Integer> uuidToIndex = new HashMap<>();
        ArrayList<GeocodeUtil.Request> reqs = new ArrayList<>();

        for (int idx = 0; idx < adapter.values.size(); idx++) {
            UserListItem item = adapter.values.get(idx);
            User user = UserStore.getUser(item.uuid);
            Location loc = user.getLastLocation();
            if (loc == null) continue;

            double distance = Double.NaN;
            if (location != null) {
                distance = location.distanceTo(loc);
            }

            if(!item.updateLocation(loc, distance)) continue;

            GeocodeUtil.Request req = new GeocodeUtil.Request(item.uuid, loc, new int[]{GeocodeUtil.ONE_LINE_SUMMARY});
            uuidToIndex.put(item.uuid, idx);
            reqs.add(req);
        }

        adapter.notifyDataSetChanged();

        GeocodeUtil.Request[] reqArr = new GeocodeUtil.Request[reqs.size()];
        reqArr = reqs.toArray(reqArr);

        geocoderTask = GeocodeUtil.Geocode(getContext(), reqArr, (resps) -> {
            swipeRefreshLayout.setRefreshing(false);
            geocoderTask = null;

            if (resps.length == 0) return;


            for (GeocodeUtil.Response resp : resps) {
                int idx = uuidToIndex.get(resp.key);
                UserListItem item = adapter.values.get(idx);

                if (!resp.success) {
                    item.updateLocationString(null);
                    continue;
                }

                for (GeocodeUtil.Response.Result r : resp.results) {
                    switch (r.type) {
                        case GeocodeUtil.ONE_LINE_SUMMARY:
                            item.updateLocationString(r.lines[0]);
                    }
                }
            }

            adapter.notifyDataSetChanged();
        });
    }

    private void makeList() {
        adapter.clear();
        Set<String> keys = UserStore.getUserKeys();
        String[] keysarr = new String[keys.size()];
        keys.toArray(keysarr);
        Arrays.sort(keysarr);
        for (String key : keysarr) {
            User user = UserStore.getUser(key);
            UserListItem ul = new UserListItem(user.uuid, user.name);
            adapter.add(ul);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        ArrayList<UserListItem> al = new ArrayList<>();
        adapter = new UserListAdapter(getActivity(), al);
        setListAdapter(adapter);

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.fragment_main_swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        long newVersion = UserStore.getVersion();
        if (listVersion != newVersion) {
            listVersion = newVersion;
            if (geocoderTask != null) {
                geocoderTask.cancel(true);
                geocoderTask = null;
            }
            makeList();
            onRefresh();
        }

        googleApiClient = new GoogleApiClient.Builder(getContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();

        googleApiClient.connect();

        Set<String> keySet = UserStore.getUserKeys();
        String[] keys = new String[keySet.size()];
        keySet.toArray(keys);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        locationSubscriber.start(
                sp.getString("server_address", ""),
                sp.getInt("server_port", 0),
                keys,
                (uuid) -> onRefresh());

        updateTimes();
    }

    public void onConnected(Bundle connectionHint) {
        onRefresh();
    }

    public void onConnectionSuspended(int cause) {}

    @Override
    public void onPause() {
        super.onPause();
        locationSubscriber.stop();

        if (googleApiClient != null) {
            googleApiClient.disconnect();
            googleApiClient = null;
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        UserListItem item = (UserListItem) getListAdapter().getItem(position);

        Intent intent = new Intent(v.getContext(), MapActivity.class);
        Bundle b = new Bundle();
        b.putString("uuid", item.uuid);
        intent.putExtras(b);

        startActivity(intent);
    }
}
