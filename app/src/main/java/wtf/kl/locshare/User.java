package wtf.kl.locshare;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import wtf.kl.locshare.crypto.Session;


class User {
    private final String username;
    private final Session session;

    private String name;
    private boolean publish;
    private int cap = 50;

    private List<Location> locations = new ArrayList<>();

    private File directory;
    private boolean dirtySettings;
    private boolean dirtyLocations;

    User(File directory, String username, Session session) {
        this.directory = directory;
        this.name = username;
        this.username = username;
        this.session = session;
        this.publish = false;
        this.dirtySettings = true;
        this.dirtyLocations = true;
    }

    String getUsername() {
        return this.username;
    }

    Session getSession() {
        return this.session;
    }

    String getName() {
        return this.name;
    }

    void setName(String name) {
        this.name = name;
    }

    boolean getPublish() {
        return this.publish;
    }

    void setPublish(boolean publish) {
        this.publish = publish;
        this.dirtySettings = true;
    }

    synchronized void addLocation(Location l) {
        while (locations.size() >= cap) {
            locations.remove(0);
        }

        locations.add(l);
        this.dirtyLocations = true;
    }

    synchronized Location[] getLocations(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("max must be one or greater");
        }

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

    synchronized Location getLastLocation() {
        if (locations.size() > 0) {
            return locations.get(locations.size() - 1);
        }
        return null;
    }

    synchronized void load() throws IOException {
        File settingsFile = new File(this.directory, "settings");
        File locationsFile = new File(this.directory, "locations");

        byte[] settingsBytes = StorageUtils.readFile(settingsFile);
        byte[] locationsBytes = StorageUtils.readFile(locationsFile);

        try {
            JSONObject settingsObj = new JSONObject(new String(settingsBytes,
                    StandardCharsets.UTF_8));
            this.name = settingsObj.getString("name");
            this.publish = settingsObj.getBoolean("publish");
            this.cap = settingsObj.getInt("cap");
            this.dirtySettings = false;

            JSONArray locationsArr = new JSONArray(new String(locationsBytes,
                    StandardCharsets.UTF_8));
            List<Location> locations = new ArrayList<>();
            for (int i = 0; i < locationsArr.length(); i++) {
                Location loc = LocationCodec.fromJSON(locationsArr.getJSONObject(i));
                locations.add(loc);
            }
            this.locations = locations;
            this.dirtyLocations = false;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    synchronized void store() throws IOException {
        if (!dirtySettings && !dirtyLocations)
            return;

        this.directory.mkdirs();

        if (dirtySettings) {
            try {
                File settingsFile = new File(this.directory, "settings");
                settingsFile.createNewFile();

                JSONObject settingsObj = new JSONObject();
                settingsObj.put("name", this.name);
                settingsObj.put("publish", this.publish);
                settingsObj.put("cap", this.cap);

                StorageUtils.writeToFile(settingsFile,
                        settingsObj.toString().getBytes(StandardCharsets.UTF_8));
                this.dirtySettings = false;
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }

        if (dirtyLocations) {
            try {
                File locationsFile = new File(this.directory, "locations");
                locationsFile.createNewFile();

                JSONArray locationsArr = new JSONArray();
                for (Location loc : this.locations) {
                    locationsArr.put(LocationCodec.toJSON(loc));
                }
                StorageUtils.writeToFile(locationsFile,
                        locationsArr.toString().getBytes(StandardCharsets.UTF_8));
                this.dirtyLocations = false;
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }
    }

    synchronized void delete() {
        StorageUtils.delete(this.directory);
    }
}
