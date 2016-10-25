package wtf.kl.locshare;

import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

class User {
    final String uuid;
    final byte[] localPrivKey;
    final byte[] localPubKey;
    final byte[] remotePubKey;
    final String name;
    final boolean publish;
    final boolean subscribe;
    final int cap;

    private ArrayList<Location> locations = new ArrayList<>();

    User(String uuid, String name, boolean publish, boolean subscribe, byte[] localPrivKey,
         byte[] localPubKey, byte[] remotePubKey, int cap) {
        this.uuid = uuid;
        this.name = name;
        this.publish = publish;
        this.subscribe = subscribe;
        this.localPrivKey = localPrivKey;
        this.localPubKey = localPubKey;
        this.remotePubKey = remotePubKey;
        this.cap = cap;
    }

    String localAsBase64() {
        if (localPubKey == null) return "";
        return Base64.encodeToString(localPubKey,Base64.NO_WRAP);
    }

    String localPrivAsBase64() {
        if (localPrivKey == null) return "";
        return Base64.encodeToString(localPrivKey, Base64.NO_WRAP);
    }

    String remoteAsBase64() {
        if (remotePubKey == null) return "";
        return Base64.encodeToString(remotePubKey, Base64.NO_WRAP);
    }

    void setLocationArray(ArrayList<Location> locations) {
        synchronized (this) {
            this.locations = locations;
        }
    }

    ArrayList<Location> cloneLocationArray() {
        synchronized (this) {
            return new ArrayList<>(locations);
        }
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
            obj.put("publish", publish);
            obj.put("subscribe", subscribe);
            String b64priv = Base64.encodeToString(localPrivKey, Base64.NO_WRAP);
            obj.put("localPrivKey", b64priv);
            String b64pub = Base64.encodeToString(localPubKey, Base64.NO_WRAP);
            obj.put("localPubKey", b64pub);
            String b64remote = Base64.encodeToString(remotePubKey, Base64.NO_WRAP);
            obj.put("remotePubKey", b64remote);
            obj.put("cap", cap);

            for (Location location : locations) {
                arr.put(LocationCodec.toJSON(location));
            }
            obj.put("locations", arr);
        }

        return obj;
    }

    @NonNull
    static User fromJSON(JSONObject obj) throws JSONException {
        User user = new User(
                obj.getString("uuid"),
                obj.getString("name"),
                obj.getBoolean("publish"),
                obj.getBoolean("subscribe"),
                Base64.decode(obj.getString("localPrivKey"), Base64.NO_WRAP),
                Base64.decode(obj.getString("localPubKey"), Base64.NO_WRAP),
                Base64.decode(obj.getString("remotePubKey"), Base64.NO_WRAP),
                obj.getInt("cap")
        );

        JSONArray arr = obj.getJSONArray("locations");
        for (int i = 0; i < arr.length(); i++) {
            Location location = LocationCodec.fromJSON(arr.getJSONObject(i));
            user.locations.add(location);
        }
        return user;
    }
}
