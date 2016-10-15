package wtf.kl.locshare;

import android.location.Location;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

class LocationCodec {

    public static byte[] encode(final Location location) {
        Log.v("codec", "Time out: " + Long.toString(location.getTime()));
        ByteBuffer test = ByteBuffer.allocate(8);
        test.putLong(location.getTime());

        Log.v("codec", "Time test: " + Long.toString(test.getLong(0)));

        ByteBuffer buffer = ByteBuffer.allocate(7 * 8);
        buffer.putLong(location.getTime());
        buffer.putDouble(location.getAccuracy());
        buffer.putDouble(location.getLatitude());
        buffer.putDouble(location.getLongitude());
        buffer.putDouble(location.getAltitude());
        buffer.putDouble(location.getBearing());
        buffer.putDouble(location.getSpeed());


        return buffer.array();
    }

    public static Location decode(byte[] input) {
        Location location = new Location("wtf.kl.locshare.LocationCodec");
        ByteBuffer buffer = ByteBuffer.wrap(input);

        location.setTime(buffer.getLong());
        location.setAccuracy((float)buffer.getDouble());
        location.setLatitude(buffer.getDouble());
        location.setLongitude(buffer.getDouble());
        location.setAltitude(buffer.getDouble());
        location.setBearing((float)buffer.getDouble());
        location.setSpeed((float)buffer.getDouble());

        Log.v("codec", "Time in: " + Long.toString(location.getTime()));

        return location;
    }

    public static Location fromJSON(JSONObject obj) throws JSONException {
        Location location = new Location("wtf.kl.locshare.LocationCodec");

        location.setTime(obj.getLong("time"));

        location.setAccuracy((float)obj.getDouble("accuracy"));
        location.setLatitude(obj.getDouble("latitude"));
        location.setLongitude(obj.getDouble("longitude"));
        location.setAltitude(obj.getDouble("altitude"));
        location.setBearing((float)obj.getDouble("bearing"));
        location.setSpeed((float)obj.getDouble("speed"));

        return location;
    }

    public static JSONObject toJSON(final Location location) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("time", location.getTime());
        obj.put("accuracy", location.getAccuracy());
        obj.put("latitude", location.getLatitude());
        obj.put("longitude", location.getLongitude());
        obj.put("altitude", location.getAltitude());
        obj.put("bearing", location.getBearing());
        obj.put("speed", location.getSpeed());

        return obj;
    }
}
