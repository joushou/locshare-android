package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.Toolbar;

public class EditUserActivity extends Activity {
    private String username = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_user, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.edit_done:
                try {
                    UsersStore users = Storage.getInstance().getUsersStore();

                    if (username == null || username.isEmpty()) {
                        // This isn't supposed to happen!
                        finish();
                        return false;
                    }

                    View view = getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }

                    boolean publish = ((Switch) findViewById(R.id.edit_publish)).isChecked();
                    String name = ((TextInputEditText) findViewById(R.id.edit_name)).getText().
                            toString();

                    User user = users.getUser(username);
                    user.setPublish(publish);
                    user.setName(name);
                    user.store();

                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast toast = Toast.makeText(this, "Unexpected error occurred", Toast.LENGTH_SHORT);
                    toast.show();
                }
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
        setContentView(R.layout.activity_edit_user);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.ic_clear_24dp);
        }

        Intent intent = getIntent();
        Bundle extra = intent.getExtras();
        if (extra == null) {
            finish();
            return;
        }

        username = extra.getString("username");

        UsersStore users = Storage.getInstance().getUsersStore();
        User user = users.getUser(username);
        if (user == null) {
            finish();
            return;
        }

        TextInputEditText username = (TextInputEditText) findViewById(R.id.edit_username);
        TextInputEditText name = (TextInputEditText) findViewById(R.id.edit_name);
        Switch publish = (Switch) findViewById(R.id.edit_publish);

        username.setText(user.getUsername());
        name.setText(user.getName());
        publish.setChecked(user.getPublish());
    }
}
