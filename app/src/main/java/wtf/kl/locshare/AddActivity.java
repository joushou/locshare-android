package wtf.kl.locshare;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.support.design.widget.Snackbar;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toolbar;

import org.whispersystems.curve25519.Curve25519KeyPair;

public class AddActivity extends Activity {
    private Curve25519KeyPair kp = null;
    private boolean remotePubKeyWarned = false;

    private class KeyGenerator extends AsyncTask<Void, Void, Curve25519KeyPair> {
        protected Curve25519KeyPair doInBackground(Void... params) {
            return CryptoManager.generateCurve25519KeyPair();
        }

        protected void onPostExecute(Curve25519KeyPair _kp) {
            kp = _kp;
            String privKey = Base64.encodeToString(kp.getPrivateKey(), Base64.URL_SAFE|Base64.NO_WRAP);
            String pubKey = Base64.encodeToString(kp.getPublicKey(), Base64.URL_SAFE|Base64.NO_WRAP);
            ((TextView)findViewById(R.id.add_local_privkey)).setText(privKey);
            ((TextView)findViewById(R.id.add_local_pubkey)).setText(pubKey);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_add, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.edit_done:

                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                if (kp == null) {
                    return false;
                }


                if (((TextView)findViewById(R.id.add_local_privkey)).getText().toString().isEmpty()) {
                    return false;
                }

                String name = ((TextView)findViewById(R.id.add_name)).getText().toString();
                String pubKey = ((TextView)findViewById(R.id.add_remote_pubkey)).getText().toString();
                String genPubKey = ((TextView)findViewById(R.id.add_local_pubkey)).getText().toString();

                if (name.isEmpty()) {
                    Snackbar.make(findViewById(android.R.id.content), "No name set", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();

                    return false;
                }

                byte[] pubKeyBytes;

                try {
                    pubKeyBytes = Base64.decode(pubKey, Base64.URL_SAFE | Base64.NO_WRAP);
                } catch (IllegalArgumentException e) {
                    Snackbar.make(findViewById(android.R.id.content), "Invalid remote public key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();

                    return false;
                }

                if (!remotePubKeyWarned && (pubKeyBytes == null || pubKeyBytes.length == 0)) {
                    Snackbar.make(findViewById(android.R.id.content), "No remote public key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    remotePubKeyWarned = true;
                    return false;
                }

                if (pubKeyBytes != null && pubKeyBytes.length != 0 && pubKeyBytes.length != 32) {
                    Snackbar.make(findViewById(android.R.id.content), "Invalid remote public key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                User user = new User(genPubKey, kp.getPrivateKey(), kp.getPublicKey(), pubKeyBytes, name);
                UserStore.addUser(user);

                finish();

                return false;
            case android.R.id.home:
                finish();
                return false;
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.ic_clear_24dp);
        }

        new KeyGenerator().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

}
