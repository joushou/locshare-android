package wtf.kl.locshare;

import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class ResultReceiverWrapper extends ResultReceiver {
    interface OnReceiveResult {
        void onReceiveResult(int resultCode, Bundle result);
    }

    private final OnReceiveResult cb;

    public ResultReceiverWrapper(OnReceiveResult cb) {
        super(null);
        this.cb = cb;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle result) {
        cb.onReceiveResult(resultCode, result);
    }
}

class UserListAdapter extends ArrayAdapter<UserListItem> {
    static class ViewHolder {
        TextView nameView;
        TextView locView;
        TextView distView;
    }
    private final Context context;
    public final ArrayList<UserListItem> values;

    public UserListAdapter(Context context, ArrayList<UserListItem> values) {
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

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        UserListItem u = values.get(position);
        if (u != null) {
            viewHolder.nameView.setText(u.name);
            viewHolder.locView.setText(u.getLocationString());
            viewHolder.distView.setText(u.getDistanceString());
        }

        return convertView;
    }
}

class UserListItem {
    public final String name;
    public final String uuid;
    private Location location;

    private String locationString;
    private double distance;

    public UserListItem(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public void updateLocation(Location loc, String locstr, double distance) {
        this.locationString = locstr;
        this.location = loc;
        this.distance = distance;
    }

    public boolean locationStringValid(Location loc) {
        if (locationString == null || locationString.isEmpty())
            return false;

        if (this.location.getAccuracy() > loc.getAccuracy() * 1.5)
            return false;

        return this.location.distanceTo(loc) < 10;
    }

    public String getLocationString() {
        if (location == null)
            return "Location not known";

        if (locationString != null && !locationString.isEmpty())
            return locationString;

        return String.format("%.8f, %.8f", location.getLatitude(), location.getLongitude());
    }

    public String getDistanceString() {
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
}

/**
 * A placeholder fragment containing a simple view.
 */
public class UserListFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener, GoogleApiClient.ConnectionCallbacks {
    private SwipeRefreshLayout swipeRefreshLayout;
    private GoogleApiClient googleApiClient = null;
    private UserListAdapter adapter = null;
    private GeocoderTask geocoderTask = null;
    private long listVersion = 0;
    private Location location = null;

    private final ResultReceiverWrapper resultReceiver = new ResultReceiverWrapper((resultCode, bundle) -> onRefresh());


    private String geocode(Context context, final Location location) {
        if (context == null) {
            return "";
        }

        Geocoder gcd = new Geocoder(context, Locale.getDefault());

        try {
            List<Address> addresses = gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if (addresses.size() == 0) {
                return null;
            }
            Address addr = addresses.get(0);

            String locationStr = "";


            String thoroughfare = addr.getThoroughfare();
            String sublocality = addr.getSubLocality();
            String locality = addr.getLocality();
            String country = addr.getCountryName();

            if (thoroughfare != null) {
                locationStr += thoroughfare;
            }

            if (locality != null) {
                if (!locationStr.isEmpty()) {
                    locationStr += ", ";
                }

                if (sublocality != null)
                    locationStr += sublocality + ", " + locality;
                else
                    locationStr += locality;
            }

            if (!locationStr.isEmpty()) {
                locationStr += ", ";
            }
            locationStr += country;

            return locationStr;
        } catch (IOException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    private class GeocoderTask extends AsyncTask<Void, Void, Void> {
        private final Context context;
        private final Location mloc;
        public GeocoderTask(Context context, Location location) {
            this.context = context;
            this.mloc = location;
        }

        protected Void doInBackground(Void... params) {

            // NOTE(kl): Not safe! Geocoder might run while user adds... things!
            synchronized(adapter) {
                for (UserListItem item : adapter.values) {
                    if (isCancelled()) return null;

                    User user = UserStore.getUser(item.uuid);
                    Location loc = user.getLastLocation();
                    if (loc == null) continue;
                    if (item.locationStringValid(loc)) continue;

                    double distance = Double.NaN;
                    if (mloc != null) {
                        distance = mloc.distanceTo(loc);
                    }

                    String locstr = geocode(context, loc);
                    item.updateLocation(loc, locstr, distance);
                }
            }

            return null;
        }

        protected void onPostExecute(Void v) {
            if (swipeRefreshLayout != null)
                swipeRefreshLayout.setRefreshing(false);
            adapter.notifyDataSetChanged();
            geocoderTask = null;
        }
    }

    public UserListFragment() {}

    public void onRefresh() {
        if (adapter == null || geocoderTask != null) return;

        geocoderTask = new GeocoderTask(getContext(), location);
        geocoderTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
            synchronized(adapter) {
                makeList();
            }
            onRefresh();
        }

        googleApiClient = new GoogleApiClient.Builder(getContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();

        googleApiClient.connect();

        Intent intent = new Intent(getActivity(), LocationListener.class);
        intent.putExtra("receiver", resultReceiver);
        getActivity().startService(intent);
    }

    public void onConnected(Bundle connectionHint) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setExpirationDuration(500);

        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, (loc) -> {
                location = loc;
                onRefresh();
            });
        } catch (SecurityException e) {

        }
    }

    public void onConnectionSuspended(int cause) {}

    @Override
    public void onPause() {
        super.onPause();
        Intent intent = new Intent(getActivity(), LocationListener.class);
        getActivity().stopService(intent);

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
