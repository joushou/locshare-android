package wtf.kl.locshare;

import android.location.Location;
import android.os.Handler;
import android.util.Base64;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class Client {
    static class AuthException extends Exception {
        static final long serialVersionUID = 0;
        public AuthException() { super(); }
        public AuthException(String message) { super(message); }
        public AuthException(String message, Throwable cause) { super(message, cause); }
        public AuthException(Throwable cause) { super(cause); }
    }
    static class ClientException extends Exception {
        static final long serialVersionUID = 0;
        public ClientException() { super(); }
        public ClientException(String message) { super(message); }
        public ClientException(String message, Throwable cause) { super(message, cause); }
        public ClientException(Throwable cause) { super(cause); }
    }
    static class ServerException extends Exception {
        static final long serialVersionUID = 0;
        public ServerException() { super(); }
        public ServerException(String message) { super(message); }
        public ServerException(String message, Throwable cause) { super(message, cause); }
        public ServerException(Throwable cause) { super(cause); }
    }

    private static class ClientInstance extends WebSocketAdapter {
        String url = "";
        String token = "";
        String username = "";
        final OkHttpClient client = new OkHttpClient();
        final Handler handler = new Handler();

        WebSocket ws = null;
        int subscriberCount = 0;

        @Override
        public void onTextMessage(WebSocket websocket, String message) throws Exception {
            try {
                JSONObject obj = new JSONObject(message);
                String source = obj.getString("source");
                String content = obj.getString("content");
                byte[] rawMsg = Base64.decode(content, Base64.NO_WRAP);


                User user = UserStore.getUser(source);
                byte[] p = CryptoManager.decryptWithCurve25519PrivateKey(rawMsg, user.localPrivKey);
                Location location = LocationCodec.decode(p);
                user.addLocation(location);
                UserStore.notifyUpdate(source);
            } catch (Exception e) {
                e.printStackTrace();
                // PASS
            }
        }

        @Override
        public void onError(WebSocket websocket, WebSocketException cause) {
            cause.printStackTrace();
        }
    }

    private static final ClientInstance instance = new ClientInstance();

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType TEXT
            = MediaType.parse("text/plain; charset=utf-8");
    private static final MediaType BINARY
            = MediaType.parse("application/octet-stream");

    private static String authHeader() { return "LOCSHARE " + instance.token; }

    static void setURL(String url) {
        if (url.charAt(url.length()-1) == '/') {
            url = url.substring(0, url.length()-1);
        }
        instance.url = url;
    }
    static String getToken() { return instance.token; }
    static void setToken(String token) { instance.token = token; }
    static String getUsername() { return instance.username; }
    static void setUsername(String username) { instance.username = username; }

    static void create(String username, String password, String name)
        throws AuthException, ClientException, ServerException, IOException, JSONException {
        JSONObject obj = new JSONObject();
        obj.put("username", username);
        obj.put("password", password);
        obj.put("name", name);

        RequestBody body = RequestBody.create(JSON, obj.toString());
        Request request = new Request.Builder()
                .url(instance.url + "/user")
                .post(body)
                .build();

        Call call = instance.client.newCall(request);

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                JSONObject err;
                switch (response.code()) {
                    case 400:
                    case 404:
                        err = new JSONObject(response.body().string());
                        throw new ClientException(err.getString("error"));
                    case 401:
                        err = new JSONObject(response.body().string());
                        throw new AuthException(err.getString("error"));
                    case 500:
                        err = new JSONObject(response.body().string());
                        throw new ServerException(err.getString("error"));
                    default:
                        throw new ClientException("error: " + Integer.toString(response.code()));
                }
            }
        }
    }

    static String login(String username, String password, String[] capabilities)
            throws AuthException, ClientException, ServerException, IOException, JSONException {
        JSONObject obj = new JSONObject();
        obj.put("username", username);
        obj.put("password", password);

        JSONArray arr = new JSONArray();
        for (String cap : capabilities) {
            arr.put(cap);
        }
        obj.put("capabilities", arr);

        RequestBody body = RequestBody.create(JSON, obj.toString());

        Request request = new Request.Builder()
                .url(instance.url + "/auth")
                .post(body)
                .build();

        Call call = instance.client.newCall(request);

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                JSONObject err;
                switch (response.code()) {
                    case 400:
                    case 404:
                        err = new JSONObject(response.body().string());
                        throw new ClientException(err.getString("error"));
                    case 401:
                        err = new JSONObject(response.body().string());
                        throw new AuthException(err.getString("error"));
                    case 500:
                        err = new JSONObject(response.body().string());
                        throw new ServerException(err.getString("error"));
                    default:
                        throw new ClientException("error: " + Integer.toString(response.code()));
                }
            }

            return response.body().string();
        }
    }

    static void changePassword(String username, String oldPassword, String newPassword)
            throws AuthException, ClientException, ServerException, IOException, JSONException {
        if (instance.token.isEmpty()) {
            throw new AuthException("no token");
        }
        JSONObject obj = new JSONObject();
        obj.put("oldPassword", oldPassword);
        obj.put("newPassword", newPassword);
        RequestBody body = RequestBody.create(JSON, obj.toString());

        Request request = new Request.Builder()
                .url(instance.url + "/user/" + username + "/password")
                .post(body)
                .build();

        Call call = instance.client.newCall(request);

        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                JSONObject err;
                switch (response.code()) {
                    case 400:
                    case 404:
                        err = new JSONObject(response.body().string());
                        throw new ClientException(err.getString("error"));
                    case 401:
                        err = new JSONObject(response.body().string());
                        throw new AuthException(err.getString("error"));
                    case 500:
                        err = new JSONObject(response.body().string());
                        throw new ServerException(err.getString("error"));
                    default:
                        throw new ClientException("error: " + Integer.toString(response.code()));
                }
            }
        }
    }

    static String setName(String username, String name)
            throws AuthException, ClientException, ServerException, IOException, JSONException {
        if (instance.token.isEmpty()) {
            throw new AuthException("no token");
        }
        RequestBody body = RequestBody.create(TEXT, name);

        Request request = new Request.Builder()
                .url(instance.url + "/user/" + username + "/name")
                .addHeader("Authorization", authHeader())
                .put(body)
                .build();

        Call call = instance.client.newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                JSONObject err;
                switch (response.code()) {
                    case 400:
                        err = new JSONObject(response.body().string());
                        throw new ClientException(err.getString("error"));
                    case 401:
                         err = new JSONObject(response.body().string());
                        throw new AuthException(err.getString("error"));
                    case 500:
                        err = new JSONObject(response.body().string());
                        throw new ServerException(err.getString("error"));
                    default:
                        throw new ClientException("error: " + Integer.toString(response.code()));
                }
            }

            return response.body().string();
        }
    }

    static  String getName(String username)
            throws AuthException, ClientException, ServerException, IOException, JSONException {
        if (instance.token.isEmpty()) {
            throw new AuthException("no token");
        }
        Request request = new Request.Builder()
                .url(instance.url + "/user/" + username + "/name")
                .addHeader("Authorization", authHeader())
                .build();

        Call call = instance.client.newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                JSONObject err;
                switch (response.code()) {
                    case 400:
                    case 404:
                        err = new JSONObject(response.body().string());
                        throw new ClientException(err.getString("error"));
                    case 401:
                        err = new JSONObject(response.body().string());
                        throw new AuthException(err.getString("error"));
                    case 500:
                        err = new JSONObject(response.body().string());
                        throw new ServerException(err.getString("error"));
                    default:
                        throw new ClientException("error: " + Integer.toString(response.code()));
                }
            }

            return response.body().string();
        }
    }

    static void push(String username, byte[] content)
            throws AuthException, ClientException, ServerException, IOException, JSONException {
        if (instance.token.isEmpty()) {
            throw new AuthException("no token");
        }
        RequestBody body = RequestBody.create(BINARY, content);

        Request request = new Request.Builder()
                .url(instance.url + "/user/" + username + "/message")
                .addHeader("Authorization", authHeader())
                .put(body)
                .build();

        Call call = instance.client.newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                JSONObject err;
                switch (response.code()) {
                    case 400:
                    case 404:
                        err = new JSONObject(response.body().string());
                        throw new ClientException(err.getString("error"));
                    case 401:
                        err = new JSONObject(response.body().string());
                        throw new AuthException(err.getString("error"));
                    case 500:
                        err = new JSONObject(response.body().string());
                        throw new ServerException(err.getString("error"));
                    default:
                        throw new ClientException("error: " + Integer.toString(response.code()));
                }
            }
        }
    }

    static void unsubscribe() {
        instance.subscriberCount--;

        instance.handler.postDelayed(() -> {
            if (instance.subscriberCount <= 0 && instance.ws != null) {
                try {
                    instance.ws.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                instance.ws = null;
            }
        }, 1000);
    }

    static void subscribe() throws AuthException {
        if (instance.token.isEmpty()) {
            throw new AuthException("no token");
        }

        instance.subscriberCount++;

        if (instance.ws != null)
            return;

        try {
            String url = instance.url;
            int idx = url.indexOf("http");
            if (idx != 0) {
                return;
            }

            url = "ws" + url.substring(4) + "/ws/subscribe";

            instance.ws = new WebSocketFactory()
                    .createSocket(url, 5000)
                    .setPingInterval(60000)
                    .addHeader("Authorization", authHeader())
                    .addListener(instance);

            ExecutorService es = Executors.newSingleThreadExecutor();
            instance.ws.connect(es).get();
        } catch (Exception e) {
            e.printStackTrace();

            if (instance.ws != null) {
                instance.ws.disconnect();
            }
            instance.ws = null;
        }
    }
}
