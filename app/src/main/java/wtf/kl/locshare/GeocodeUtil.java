package wtf.kl.locshare;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

class GeocodeUtil {
    static final int FULL_ADDRESS = 0;
    static final int TWO_LINE_SUMMARY = 1;
    static final int ONE_LINE_SUMMARY = 2;

    interface Callback {
        void onResult(Response[] resp);
    }

    static class Request {
        final String key;
        final Location location;
        final int[] types;
        private Object tag;

        Request(String key, Location location, int[] types) {
            this.key = key;
            this.location = location;
            this.types = types;
        }

        void setTag(Object tag) { this.tag = tag; }
    }

    static class Response {
        static class Result {
            String[] lines;
            int type;
        }

        String key;
        Location location;
        Result[] results;
        boolean success;
        private Object tag;

        Response() {
            success = false;
        }

        Object getTag() { return this.tag; }
    }

    static class GeocoderTask extends AsyncTask<Request, Void, Response[]> {
        private final Context context;
        private final Callback callback;

        GeocoderTask(Context context, Callback callback) {
            this.context = context;
            this.callback = callback;
        }

        private static String[] fullAddress(Address addr) {
            String locationStr = "";
            for (int i = 0; i < addr.getMaxAddressLineIndex(); i++) {
                locationStr += addr.getAddressLine(i) + "\n";
            }
            locationStr += addr.getCountryName();
            return new String[]{locationStr};
        }

        private static String[] oneLineSummary(Address addr) {
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
            return new String[]{locationStr};
        }

        private static String[] twoLineSummary(Address addr) {
            String thoroughfare = addr.getThoroughfare();
            String locality = addr.getLocality();
            String adminArea = addr.getAdminArea();
            String country = addr.getCountryName();

            String[] res = new String[2];

            if (thoroughfare != null && adminArea != null && locality != null && country != null) {
                res[0] = thoroughfare;
                res[1] = locality + ", " + adminArea + ", " + country;
            } else if (thoroughfare != null && locality != null && country != null) {
                res[0] = thoroughfare;
                res[1] = locality + ", " + country;
            } else if (locality != null && adminArea != null && country != null) {
                res[0] = locality;
                res[1] = adminArea + ", " + country;
            } else if (locality != null && country != null) {
                res[0] = locality;
                res[1] = country;
            } else if (thoroughfare != null && adminArea != null && country != null) {
                res[0] = thoroughfare;
                res[1] = adminArea + ", " + country;
            } else if (thoroughfare != null && country != null) {
                res[0] = thoroughfare;
                res[1] = country;
            } else if (adminArea != null && country != null) {
                res[0] = adminArea;
                res[1] = country;
            } else {
                return null;
            }

            return res;
        }

        protected Response[] doInBackground(Request ...params) {
            Response[] ar = new Response[params.length];

            Geocoder gcd = new Geocoder(context, Locale.getDefault());
            for (int idx = 0; idx < params.length; idx++) {
                Request r = params[idx];
                Response resp = new Response();
                ar[idx] = resp;
                resp.key = r.key;
                resp.location = r.location;
                resp.tag = r.tag;

                try {
                    List<Address> addresses = gcd.getFromLocation(r.location.getLatitude(), r.location.getLongitude(), 1);
                    if (addresses.size() == 0) {
                        continue;
                    }

                    Address addr = addresses.get(0);
                    resp.results = new Response.Result[r.types.length];

                    for (int ydx = 0; ydx < r.types.length; ydx++) {
                        resp.results[ydx] = new Response.Result();
                        resp.results[ydx].type = r.types[ydx];
                        switch (r.types[ydx]) {
                            case FULL_ADDRESS:
                                resp.results[ydx].lines = fullAddress(addr);
                                break;
                            case ONE_LINE_SUMMARY:
                                resp.results[ydx].lines = oneLineSummary(addr);
                                break;
                            case TWO_LINE_SUMMARY:
                                resp.results[ydx].lines = twoLineSummary(addr);
                                break;
                        }
                    }
                    resp.success = true;

                } catch (IOException | IllegalArgumentException e) {
                    // PASS
                }
            }

            return ar;
        }

        protected void onPostExecute(Response[] results) {
            callback.onResult(results);
        }
    }

    static GeocoderTask Geocode(Context context, Request[] requests, Callback callback) {
        GeocoderTask task = new GeocoderTask(context, callback);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, requests);
        return task;
    }
}
