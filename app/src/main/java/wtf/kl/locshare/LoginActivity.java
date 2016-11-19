package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

public class LoginActivity extends Activity {
    static private class Parameters {
        String username = "";
        String password = "";
    }
    static private class Result {
        String username = "";
        String token = "";
        boolean success = false;
    }
    static private class LoginTask extends AsyncTask<Parameters, Void, Result> {
        private Activity context;

        LoginTask(Activity context) {
            this.context = context;
        }

        @Override
        protected Result doInBackground(Parameters ...params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("LoginTask must have 1 parameter");
            }

            String username = params[0].username;
            String password = params[0].password;

            Result result = new Result();
            try {
                String token = Client.login(username, password, new String[]{
                        "publish", "interactive"
                });
                result.username = username;
                result.token = token;
                result.success = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onCancelled(Result result) {
            context = null;
        }

        @Override
        protected void onPostExecute(Result result) {
            if (!result.success) {
                Toast toast = Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT);
                toast.show();
                context = null;
                return;
            }

            Client.setToken(result.token);
            Client.setUsername(result.username);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor edit = sp.edit();
            edit.putString("auth_token", result.token);
            edit.putString("auth_username", result.username);
            edit.apply();

            context.finish();
            context = null;
        }
    }


    private void login() {
        String username = ((EditText)findViewById(R.id.login_username)).getText().toString();
        String password = ((EditText)findViewById(R.id.login_password)).getText().toString();

        if (username.isEmpty()) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.login_username_layout);
            til.setErrorEnabled(true);
            til.setError("You need to enter a username");
            return;
        }

        if (password.isEmpty()) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.login_password_layout);
            til.setErrorEnabled(true);
            til.setError("You need to enter a password");
            return;
        }


        Parameters p = new Parameters();
        p.username = username;
        p.password = password;

        new LoginTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, p);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.login_login:
                login();
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
        setContentView(R.layout.activity_login);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.ic_clear_24dp);
        }

        TextInputEditText password = (TextInputEditText) findViewById(R.id.login_password);
        TextInputEditText username = (TextInputEditText) findViewById(R.id.login_username);

        TextView.OnEditorActionListener f = (v, actionId, event) -> {
            switch (actionId) {
                case EditorInfo.IME_ACTION_DONE:
                    login();
                    return true;
            }
            return false;
        };

        password.setOnEditorActionListener(f);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        username.setText(sp.getString("auth_username", ""));
    }
}
