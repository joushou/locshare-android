package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Toolbar;

public class NewAccountActivity extends AppCompatActivity {
    static private class Parameters {
        String username = "";
        String password = "";
        String name = "";
    }
    static private class Result {
        String username = "";
        String token = "";
        boolean success = false;
    }
    static private class NewAccountTask extends AsyncTask<Parameters, Void, Result> {
        private final Activity context;

        NewAccountTask(Activity context) {
            this.context = context;
        }

        protected Result doInBackground(Parameters ...params) {
            if (params.length != 1) {
                throw new IllegalArgumentException("LoginTask must have 1 parameter");
            }

            String username = params[0].username;
            String password = params[0].password;
            String name = params[0].name;

            Result result = new Result();
            try {
                Client.create(username, password, name);
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

        protected void onPostExecute(Result result) {
            if (!result.success) {
                Toast toast = Toast.makeText(context, "Account creation failed", Toast.LENGTH_SHORT);
                toast.show();
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
        }
    }

    private void newAccount() {
        String username = ((EditText)findViewById(R.id.new_account_username)).getText().toString();
        String name = ((EditText)findViewById(R.id.new_account_name)).getText().toString();
        String password = ((EditText)findViewById(R.id.new_account_password)).getText().toString();
        String password_repeat = ((EditText)findViewById(R.id.new_account_password_repeat)).getText().toString();

        if (name.isEmpty()) {
            TextInputLayout til = (TextInputLayout) findViewById(R.id.new_account_name_layout);
            til.setErrorEnabled(true);
            til.setError("You need to enter a name");
            return;
        }

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
        p.name = name;

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
