package wtf.kl.locshare;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

class UserListItem {
    final User user;
    Location location;
    private double distance;
    private String locationString;

    UserListItem(User user) { this.user = user; }

    boolean updateLocationFrom(Location loc) {
        Location newLocation = user.getLastLocation();
        boolean needUpdate = newLocation != null && (
                location != null ||
                locationString == null ||
                location.getAccuracy() <= newLocation.getAccuracy() * 1.5 ||
                location.distanceTo(newLocation) < 10);

        this.location = newLocation;
        if (loc != null && newLocation != null) {
            this.distance = loc.distanceTo(newLocation);
        } else {
            this.distance = Double.NaN;
        }

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

class SimpleDividerItemDecoration extends RecyclerView.ItemDecoration {
    private Drawable mDivider;

    public SimpleDividerItemDecoration(Context context) {
        mDivider = context.getResources().getDrawable(R.drawable.line_divider);
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

            int top = child.getBottom() + params.bottomMargin;
            int bottom = top + mDivider.getIntrinsicHeight();

            mDivider.setBounds(left, top, right, bottom);
            mDivider.draw(c);
        }
    }
}

class UserListAdapter extends RecyclerView.Adapter<UserListAdapter.ViewHolder> {
    private String[] indexTable;
    final private Map<String, UserListItem> values;

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView nameView;
        final TextView locView;
        final TextView distView;
        final TextView timeView;
        UserListItem item;

        void onClick(View v) {
            if (item.user == null) return;

            Context context = v.getContext();
            Intent intent = new Intent(context, MapActivity.class);
            intent.putExtra("uuid", item.user.uuid);
            context.startActivity(intent);
        }

        ViewHolder(View v) {
            super(v);
            nameView = (TextView) v.findViewById(R.id.userlist_name);
            locView = (TextView) v.findViewById(R.id.userlist_location);
            distView = (TextView) v.findViewById(R.id.userlist_distance);
            timeView = (TextView) v.findViewById(R.id.userlist_time);
            v.setOnClickListener(this::onClick);
        }
    }

    UserListAdapter() {
        values = new HashMap<>();
        rebuildList();
    }

    void rebuildList() {
        values.clear();
        Set<String> userKeys = UserStore.getUserKeys();
        String[] keysarr = new String[userKeys.size()];
        keysarr = userKeys.toArray(keysarr);
        Arrays.sort(keysarr);
        indexTable = keysarr;

        for (String key : keysarr) {
            User user = UserStore.getUser(key);
            UserListItem ul = new UserListItem(user);
            values.put(user.uuid, ul);
        }
    }

    @Override
    public UserListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.rowlayout_userlist, parent, false);
        return new ViewHolder(v);
    }

    UserListItem getItem(int position) {
        return values.get(indexTable[position]);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        UserListItem item = getItem(position);
        if (item == null) return;

        holder.nameView.setText(item.user.name);
        holder.locView.setText(item.getLocationString());
        holder.distView.setText(item.getDistanceString());
        holder.timeView.setText(item.getTimeString());
        holder.item = item;
    }

    @Override
    public int getItemCount() {
        return values.size();
    }
}

/**
 * A placeholder fragment containing a simple view.
 */
public class UserListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, GoogleApiClient.ConnectionCallbacks {
    private SwipeRefreshLayout swipeRefreshLayout;
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

        ArrayList<GeocodeUtil.Request> reqs = new ArrayList<>();

        for (int idx = 0; idx < adapter.getItemCount(); idx++) {
            UserListItem item = adapter.getItem(idx);
            if (!item.updateLocationFrom(location))
                continue;

            GeocodeUtil.Request req = new GeocodeUtil.Request(item.user.uuid, item.location, new int[]{GeocodeUtil.ONE_LINE_SUMMARY});
            req.setTag(item);
            reqs.add(req);
        }

        adapter.notifyDataSetChanged();

        GeocodeUtil.Request[] reqArr = new GeocodeUtil.Request[reqs.size()];
        reqArr = reqs.toArray(reqArr);

        geocoderTask = GeocodeUtil.Geocode(getContext(), reqArr, (resps) -> {
            swipeRefreshLayout.setRefreshing(false);
            geocoderTask = null;

            for (GeocodeUtil.Response resp : resps) {
                UserListItem item = (UserListItem) resp.getTag();

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_userlist, container, false);

        adapter = new UserListAdapter();
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.userlist_recyclerview);
        recyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getContext()));

        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.userlist_swiperefreshlayout);
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
            adapter.rebuildList();
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
}
