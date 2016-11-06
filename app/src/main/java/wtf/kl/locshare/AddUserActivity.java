package wtf.kl.locshare;

import android.app.ActionBar;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.support.design.widget.TextInputLayout;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import org.whispersystems.curve25519.Curve25519KeyPair;

import java.io.IOException;

public class AddUserActivity extends Activity {
    private Curve25519KeyPair kp = null;

    private class KeyGenerator extends AsyncTask<Void, Void, Curve25519KeyPair> {
        protected Curve25519KeyPair doInBackground(Void... params) {
            return CryptoManager.generateCurve25519KeyPair();
        }

        protected void onPostExecute(Curve25519KeyPair _kp) {
            kp = _kp;
            String privKey = Base64.encodeToString(kp.getPrivateKey(), Base64.NO_WRAP);
            String pubKey = Base64.encodeToString(kp.getPublicKey(), Base64.NO_WRAP);
            ((TextView)findViewById(R.id.add_local_privkey)).setText(privKey);
            ((TextView)findViewById(R.id.add_local_pubkey)).setText(pubKey);
        }
    }
    static private class Parameters {
        String username = "";
        byte[] pubKey = null;
        boolean publish = false;
        boolean subscribe = false;
        Curve25519KeyPair kp = null;
    }
    static private class Result {
        boolean success = false;
        boolean toast = false;
        int textInputLayout = -1;
        String errorMessage = "";

        static Result toast(String msg) {
            Result r = new Result();
            r.success = false;
            r.toast = true;
            r.errorMessage = msg;
            return r;
        }

        static Result editTextError(String msg, int id) {
            Result r = new Result();
            r.success = false;
            r.textInputLayout = id;
            r.errorMessage = msg;
            return r;
        }

        static Result success() {
            Result r = new Result();
            r.success = true;
            return r;
        }
    }
    static private class AddUserTask extends AsyncTask<Parameters, Void, Result> {
        private final Activity context;

        AddUserTask(Activity context) {
            this.context = context;
        }

        protected Result doInBackground(Parameters ...params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("LoginTask must have 1 parameter");
            }

            String username = params[0].username;
            byte[] pubKeyBytes = params[0].pubKey;
            boolean publish = params[0].publish;
            boolean subscribe = params[0].subscribe;
            Curve25519KeyPair kp = params[0].kp;

            String name;
            Result result = Result.success();

            try {
                name = Client.getName(username);
                User user = new User(username, name, publish, subscribe, kp.getPrivateKey(),
                        kp.getPublicKey(), pubKeyBytes, 10);
                UserStore.addUser(user);
            } catch (Client.AuthException e) {
                result = Result.toast("Please log in to add users");
            } catch (Client.ClientException e) {
                e.printStackTrace();
                result = Result.editTextError("Could not retrieve information about user", R.id.add_username_layout);
            } catch (Client.ServerException e) {
                e.printStackTrace();
                result = Result.toast("Server error occurred");
            } catch (IOException e) {
                result = Result.toast("Network error occurred");
            } catch (Exception e) {
                e.printStackTrace();
                result = Result.toast("Unexpected error occurred");
            }

            return result;
        }

        protected void onPostExecute(Result result) {
            if (result.success) {
                context.finish();
                return;
            }

            String msg = result.errorMessage;
            int id = result.textInputLayout;
            if (result.toast) {
                Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
                toast.show();
            } else {
                TextInputLayout til = (TextInputLayout) context.findViewById(id);
                til.setErrorEnabled(true);
                til.setError(msg);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_add_user, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.add_done:

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

                String username = ((TextView)findViewById(R.id.add_username)).getText().toString();
                String pubKey = ((TextView)findViewById(R.id.add_remote_pubkey)).getText().toString();
                boolean publish = ((Switch)findViewById(R.id.add_publish)).isChecked();
                boolean subscribe = ((Switch)findViewById(R.id.add_subscribe)).isChecked();

                if (username.isEmpty()) {
                    TextInputLayout til = (TextInputLayout) findViewById(R.id.add_username_layout);
                    til.setErrorEnabled(true);
                    til.setError("You need to enter a username");
                    return false;
                }

                byte[] pubKeyBytes = null;

                if (publish) {
                    try {
                        pubKeyBytes = Base64.decode(pubKey, Base64.NO_WRAP);
                    } catch (IllegalArgumentException e) {
                        TextInputLayout til = (TextInputLayout) findViewById(R.id.add_remote_pubkey_layout);
                        til.setErrorEnabled(true);
                        til.setError("Invalid remote public key");
                        return false;
                    }

                    if (pubKeyBytes == null || pubKeyBytes.length == 0) {

                        TextInputLayout til = (TextInputLayout) findViewById(R.id.add_remote_pubkey_layout);
                        til.setErrorEnabled(true);
                        til.setError("You need to enter a remote public key");
                        return false;
                    }

                    if (pubKeyBytes.length != 32) {
                        TextInputLayout til = (TextInputLayout) findViewById(R.id.add_remote_pubkey_layout);
                        til.setErrorEnabled(true);
                        til.setError("Invalid remote public key");
                        return false;
                    }
                }

                Parameters p = new Parameters();
                p.username = username;
                p.pubKey = pubKeyBytes;
                p.publish = publish;
                p.subscribe = subscribe;
                p.kp = kp;

                new AddUserTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, p);
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
        setContentView(R.layout.activity_add_user);
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
