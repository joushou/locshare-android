package wtf.kl.locshare;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Set;

public class LocationListener extends Service {
    private Listener listener = null;

    class ListenerArg {
        final ResultReceiver rr;
        final String host;
        final int port;

        ListenerArg(ResultReceiver rr, String host, int port) {
            this.rr = rr;
            this.host = host;
            this.port = port;
        }
    }

    class Listener extends AsyncTask<ListenerArg, Void, Void> {

        protected Void doInBackground(ListenerArg ...params) {

            if (params.length == 0) return null;
            ListenerArg arg = params[0];

            Socket socket = null;
            BufferedWriter os;
            BufferedReader is = null;
            try {

                socket = new Socket(arg.host, arg.port);
                os = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                is = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                for (String key : UserStore.getUserKeys()) {
                    User user = UserStore.getUser(key);
                    os.write(String.format("sub %s\n", user.localAsBase64()));
                }
                os.flush();

                while(!isCancelled()) {
                    String msg = is.readLine();
                    if (msg == null) {
                        break;
                    }

                    String[] parts = msg.split(" ");
                    if (parts.length != 2) {
                        break;
                    }

                    String uuid = parts[0];
                    String encMsg = parts[1];
                    byte[] rawMsg = Base64.decode(encMsg, Base64.NO_WRAP | Base64.URL_SAFE);

                    User user = UserStore.getUser(uuid);
                    if (user == null) {
                        continue;
                    }
                    byte[] privKey = user.localPrivKey;
                    if (privKey == null) {
                        continue;
                    }

                    byte[] p = CryptoManager.decryptWithCurve25519PrivateKey(rawMsg, privKey);

                    Location location = LocationCodec.decode(p);

                    user.addLocation(location, 10);
                    Bundle bundle = new Bundle();
                    bundle.putString("uuid", uuid);
                    arg.rr.send(0, bundle);
                }
                is.close();
                socket.close();

            } catch (IOException e) {
                if (is != null) try { is.close(); } catch (IOException x) {}
                if (socket != null) try { socket.close(); } catch (IOException x) {}
            }

            return null;
        }

        protected void onPostExecute(Void v) {

        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        ResultReceiver rr = intent.getParcelableExtra("receiver");
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        ListenerArg listenerArg = new ListenerArg(
                rr,
                sp.getString("server_address", ""),
                sp.getInt("server_port", 0)
        );

        listener = new Listener();
        listener.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, listenerArg);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (listener != null)
            listener.cancel(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
