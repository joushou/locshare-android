package wtf.kl.locshare;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class Store {
    final Map<String, User> users = new HashMap<>();
    long version = 0;
}

class UserStore {
    private static Store store = new Store();

    static Set<String> getUserKeys() {
        synchronized(store) {
            return store.users.keySet();
        }
    }

    static int size() {
        synchronized(store) {
            return store.users.size();
        }
    }

    static boolean isEmpty() {
        synchronized(store) {
            return store.users.isEmpty();
        }
    }

    static long getVersion() {
        synchronized(store) {
            return store.version;
        }
    }

    static User getUser(String uuid) {
        synchronized(store) {
            return store.users.get(uuid);
        }
    }

    static void addUser(User user) {
        synchronized(store) {
            store.version++;
            store.users.put(user.uuid, user);
        }
    }

    static void delUser(User user) {
        synchronized(store) {
            store.users.remove(user.uuid);
        }
    }

    @NonNull
    static JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray arr = new JSONArray();

        synchronized (store) {
            obj.put("version", store.version);

            Set<String> keys = store.users.keySet();
            for (String key : keys) {
                JSONObject o = store.users.get(key).toJSON();
                arr.put(o);
            }

            obj.put("users", arr);
        }

        return obj;
    }

    public static void fromJSON(JSONObject obj) throws JSONException {
        Store s = new Store();
        s.version = obj.getLong("version");

        JSONArray arr = obj.getJSONArray("users");

        for (int i = 0; i < arr.length(); i++) {
            User user = User.fromJSON(arr.getJSONObject(i));
            s.users.put(user.uuid, user);

        }

        store = s;
    }
}
