package wtf.kl.locshare;

import android.location.Location;
import android.support.annotation.NonNull;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class User {
    final byte[] localPrivKey;
    final byte[] localPubKey;
    final byte[] remotePubKey;
    final String username;
    final boolean publish;
    final boolean subscribe;
    final int cap;

    String name;

    private ArrayList<Location> locations = new ArrayList<>();

    User(String username, String name, boolean publish, boolean subscribe, byte[] localPrivKey,
         byte[] localPubKey, byte[] remotePubKey, int cap) {
        this.username = username;
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
            obj.put("username", username);
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
                obj.getString("username"),
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

class UserStore {
    public interface UpdateListener {
        void onUpdate(String source);
    }

    private static class Store {
        final Map<String, User> users;
        long version = 0;

        Store() { users = new HashMap<>(); }
        Store(Store s) {
            users = new HashMap<>(s.users);
            version = s.version+1;
        }
    }

    // Store is an immutable(!) user storage device. Modifications should not occur on this field,
    // but should be done by creating a new store that replaces the old. This is done under the
    // assumption that UserStore will be read orders of magnitude more often than it is written,
    // making lock-free reading with the significantly slower writes. Writes are synchronized among
    // eachother to ensure proper behaviour.
    private static volatile Store store = new Store();

    private static ArrayList<UpdateListener> cbs = new ArrayList<>();

    static Set<String> getUserKeys() { return store.users.keySet(); }
    static int size() { return store.users.size(); }
    static boolean isEmpty() { return store.users.isEmpty(); }
    static long getVersion() { return store.version; }
    static User getUser(String username) { return store.users.get(username); }

    static synchronized void addUpdateListener(UpdateListener cb) {
        cbs.add(cb);
    }

    static synchronized void delUpdateListener(UpdateListener cb) {
        cbs.remove(cb);
    }

    static void notifyUpdate(String source) {
        for (UpdateListener cb : cbs) {
            cb.onUpdate(source);
        }
    }

    static synchronized void addUser(User user) {
        Store s = new Store(store);
        s.users.put(user.username, user);
        store = s;
    }

    static synchronized void delUser(User user) {
        Store s = new Store(store);
        s.users.remove(user.username);
        store = s;
    }

    @NonNull
    static JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray arr = new JSONArray();

        obj.put("version", store.version);

        Set<String> keys = store.users.keySet();
        for (String key : keys) {
            JSONObject o = store.users.get(key).toJSON();
            arr.put(o);
        }

        obj.put("users", arr);

        return obj;
    }

    static void fromJSON(JSONObject obj) throws JSONException {
        Store s = new Store();
        s.version = obj.getLong("version");

        JSONArray arr = obj.getJSONArray("users");

        for (int i = 0; i < arr.length(); i++) {
            User user = User.fromJSON(arr.getJSONObject(i));
            s.users.put(user.username, user);

        }

        store = s;
    }

    static void writeToFile(File file) throws IOException, JSONException {
        file.createNewFile();
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(file, false);
            JSONObject obj = UserStore.toJSON();
            byte[] b = obj.toString().getBytes(StandardCharsets.UTF_8);

            fos.write(b);
            fos.flush();
            fos.close();
            fos = null;

        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (IOException e) {
                // PASS
            }
        }
    }

    static void readFromFile(File file) throws IOException, JSONException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] data = new byte[(int)file.length()];
            fis.read(data);
            fis.close();
            fis = null;

            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            UserStore.fromJSON(obj);
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                // PASS
            }
        }
    }
}
