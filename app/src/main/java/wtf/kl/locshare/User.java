package wtf.kl.locshare;

import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

class User {
    String uuid;
    byte[] localPrivKey;
    byte[] localPubKey;
    byte[] remotePubKey;
    int cap = 10;
    String name;

    private final ArrayList<Location> locations = new ArrayList<>();

    private User() {}

    User(String uuid, byte[] localPrivKey, byte[] localPubKey, byte[] remotePubKey, String name) {
        this.uuid = uuid;
        this.localPrivKey = localPrivKey;
        this.localPubKey = localPubKey;
        this.remotePubKey = remotePubKey;
        this.name = name;
    }

    String localAsBase64() {
        return Base64.encodeToString(localPubKey, Base64.URL_SAFE|Base64.NO_WRAP);
    }

    String localPrivAsBase64() {
        return Base64.encodeToString(localPrivKey, Base64.URL_SAFE|Base64.NO_WRAP);
    }

    String remoteAsBase64() {
        return Base64.encodeToString(remotePubKey, Base64.URL_SAFE|Base64.NO_WRAP);
    }

    void setCap(int i) {
        cap = i;
    }

    void addLocation(Location l) {
        synchronized (this) {
            while (locations.size() >= cap) {
                locations.remove(0);
            }

            locations.add(l);
        }
    }

    Location[] getLocations(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be one or greater");
        }
        synchronized (this) {
            if (locations.size() == 0) {
                return new Location[0];
            }

            if (max == 1) {
                return new Location[]{locations.get(locations.size() - 1)};
            }
            if (max > locations.size()) {
                Location[] list = new Location[locations.size()];
                return locations.toArray(list);
            }

            Location[] list = new Location[max];
            return locations.subList(locations.size() - max, locations.size()).toArray(list);
        }
    }

    Location getLastLocation() {
        synchronized (this) {
            if (locations.size() > 0) {
                return locations.get(locations.size() - 1);
            }
            return null;
        }
    }

    @NonNull
    JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray arr = new JSONArray();

        synchronized(this) {
            obj.put("uuid", uuid);
            obj.put("name", name);
            String b64priv = Base64.encodeToString(localPrivKey, Base64.NO_WRAP | Base64.URL_SAFE);
            obj.put("localPrivKey", b64priv);
            String b64pub = Base64.encodeToString(localPubKey, Base64.NO_WRAP | Base64.URL_SAFE);
            obj.put("localPubKey", b64pub);
            String b64remote = Base64.encodeToString(remotePubKey, Base64.NO_WRAP | Base64.URL_SAFE);
            obj.put("remotePubKey", b64remote);

            for (Location location : locations) {
                arr.put(LocationCodec.toJSON(location));
            }
            obj.put("locations", arr);
        }

        // TODO(kl): serialize locations as JSON.

        return obj;
    }

    @NonNull
    static User fromJSON(JSONObject obj) throws JSONException {
        User user = new User();
        user.uuid = obj.getString("uuid");
        user.name = obj.getString("name");
        user.localPrivKey = Base64.decode(obj.getString("localPrivKey"), Base64.NO_WRAP | Base64.URL_SAFE);
        user.localPubKey = Base64.decode(obj.getString("localPubKey"), Base64.NO_WRAP | Base64.URL_SAFE);
        user.remotePubKey = Base64.decode(obj.getString("remotePubKey"), Base64.NO_WRAP | Base64.URL_SAFE);

        JSONArray arr = obj.getJSONArray("locations");
        for (int i = 0; i < arr.length(); i++) {
            Location location = LocationCodec.fromJSON(arr.getJSONObject(i));
            user.locations.add(location);
        }
        return user;
    }
}
