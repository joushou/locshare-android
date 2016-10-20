package wtf.kl.locshare;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class UserStore {
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

    static Set<String> getUserKeys() { return store.users.keySet(); }
    static int size() { return store.users.size(); }
    static boolean isEmpty() { return store.users.isEmpty(); }
    static long getVersion() { return store.version; }
    static User getUser(String uuid) { return store.users.get(uuid); }

    static synchronized void addUser(User user) {
        Store s = new Store(store);
        s.users.put(user.uuid, user);
        store = s;
    }

    static synchronized void delUser(User user) {
        Store s = new Store(store);
        s.users.remove(user.uuid);
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
            s.users.put(user.uuid, user);

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
