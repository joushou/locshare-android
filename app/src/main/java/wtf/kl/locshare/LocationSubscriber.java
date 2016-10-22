package wtf.kl.locshare;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

class LocationSubscriber {
    interface OnUpdate {
        void onUpdate(String uuid);
    }

    private Listener listener = null;

    private class Listener extends AsyncTask<Void, String, Void> {

        final String host;
        final int port;
        final OnUpdate onUpdate;
        final String[] uuids;


        Listener(String host, int port, String[] uuids, OnUpdate onUpdate) {
            this.host = host;
            this.port = port;
            this.uuids = uuids;
            this.onUpdate = onUpdate;
        }

        protected Void doInBackground(Void ...params) {
            Socket socket = null;
            BufferedWriter os = null;
            BufferedReader is = null;

            try {

                socket = new Socket(host, port);
                os = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                is = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                for (String uuid : uuids) {
                    User user = UserStore.getUser(uuid);
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
                    byte[] rawMsg = Base64.decode(encMsg, Base64.NO_WRAP);

                    User user = UserStore.getUser(uuid);
                    if (user == null) {
                        continue;
                    }
                    byte[] privKey = user.localPrivKey;
                    if (privKey == null) {
                        continue;
                    }

                    try {
                        byte[] p = CryptoManager.decryptWithCurve25519PrivateKey(rawMsg, privKey);

                        Location location = LocationCodec.decode(p);

                        user.addLocation(location);
                        publishProgress(uuid);
                    } catch (IllegalArgumentException e) {
                        // PASS
                    }
                }
                os.close();
                is.close();
                socket.close();

            } catch (IOException e) {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException x) {
                        // PASS
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException x) {
                        // PASS
                    }
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException x) {
                        // PASS
                    }
                }
            }

            return null;
        }

        protected void onProgressUpdate(String ...progress) {
            for (String uuid : progress)
                onUpdate.onUpdate(uuid);
        }

        protected void onPostExecute(Void v) {}
    }

    public void start(String host, int port, String[] uuids, OnUpdate onUpdate) {
        listener = new Listener(host, port, uuids, onUpdate);
        listener.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void stop() {
        if (listener != null)
            listener.cancel(true);
        listener = null;
    }

}
