package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Toolbar;

import wtf.kl.locshare.crypto.ECKeyPair;
import wtf.kl.locshare.crypto.ECSignedPublicKey;
import wtf.kl.locshare.crypto.PreKey;
import wtf.kl.locshare.crypto.SignedPreKey;

public class NewAccountActivity extends Activity {
    static private class Parameters {
        String username = "";
        String password = "";
    }
    static private class Result {
        String username = "";
        String token = "";
        boolean success = false;
        boolean toast = false;
        String errorMessage = "";

        static Result toast(String msg) {
            Result r = new Result();
            r.success = false;
            r.toast = true;
            r.errorMessage = msg;
            return r;
        }
        static Result success() {
            Result r = new Result();
            r.success = true;
            return r;
        }
    }
    static private class NewAccountTask extends AsyncTask<Parameters, Void, Result> {
        private Activity context;

        NewAccountTask(Activity context) {
            this.context = context;
        }

        @Override
        protected Result doInBackground(Parameters ...params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("LoginTask must have 1 parameter");
            }

            String username = params[0].username;
            String password = params[0].password;

            Result result = Result.success();
            try {
                PrivateStore privateStore = Storage.getInstance().getPrivateStore();
                ECKeyPair ourIdentity = ECKeyPair.generate();
                privateStore.setOurIdentity(ourIdentity);
                Client.create(username, password);
                String token = Client.login(username, password, new String[]{
                        "publish", "interactive"
                });
                Client.setToken(token);
                Client.setUsername(username);
                Client.setIdentity(username, ourIdentity.asPublicKey());

                for (int i = 0; i < 100; i++) {
                    ECKeyPair kp = ECKeyPair.generate();
                    PreKey p = new PreKey(kp.asPublicKey(), i);
                    privateStore.setOneTimeKey(i, kp);
                    Client.setOneTimeKey(username, p);
                }

                ECKeyPair kp = ECKeyPair.generate();
                ECSignedPublicKey kp2 = ECSignedPublicKey.sign(kp.asPublicKey(),
                        ourIdentity);

                SignedPreKey temporaryKey = new SignedPreKey(kp2, 0);
                privateStore.setTemporaryKey(0, kp);
                Client.setTemporaryKey(username, temporaryKey);

                privateStore.store();
                result.username = username;
                result.token = token;
                result.success = true;
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return Result.toast("Account creation failed");
            }
        }

        @Override
        protected void onCancelled(Result result) {
            context = null;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (!result.success) {
                if (result.toast) {
                    Toast toast = Toast.makeText(context, result.errorMessage, Toast.LENGTH_SHORT);
                    toast.show();
                }
                context = null;
                return;
            }

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor edit = sp.edit();
            edit.putString("auth_token", result.token);
            edit.putString("auth_username", result.username);
            edit.apply();

            context.finish();
            context = null;
        }
    }

    private void newAccount() {
        String username = ((EditText)findViewById(R.id.new_account_username)).getText().toString();
        String password = ((EditText)findViewById(R.id.new_account_password)).getText().toString();
        String password_repeat = ((EditText)findViewById(R.id.new_account_password_repeat)).getText().toString();

        if (username.isEmpty()) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.new_account_username_layout);
            til.setErrorEnabled(true);
            til.setError("You need to enter a username");
            return;
        }

        if (password.isEmpty()) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.new_account_password_layout);
            til.setErrorEnabled(true);
            til.setError("You need to enter a password");
            return;
        }

        if (password_repeat.isEmpty()) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.new_account_password_repeat_layout);
            til.setErrorEnabled(true);
            til.setError("You need to enter a password");
            return;
        }

        if (!password_repeat.equals(password)) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.new_account_password_repeat_layout);
            til.setErrorEnabled(true);
            til.setError("Passwords do not match");
            return;
        }

        Parameters p = new Parameters();
        p.username = username;
        p.password = password;

        new NewAccountTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, p);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_new_account, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.new_account_create:
                newAccount();
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
        setContentView(R.layout.activity_new_account);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.ic_clear_24dp);
        }
    }
}
