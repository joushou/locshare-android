package wtf.kl.locshare;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;

public class EditActivity extends Activity {
    private String uuid = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.edit_done:
                if (uuid == null || uuid.isEmpty()) {
                    // This isn't supposed to happen!
                    finish();
                    return false;
                }

                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                String name = ((EditText)findViewById(R.id.edit_name)).getText().toString();
                String localPrivKey = ((EditText)findViewById(R.id.edit_local_privkey)).getText().toString();
                String remotePubKey = ((EditText)findViewById(R.id.edit_remote_pubkey)).getText().toString();

                if (localPrivKey.isEmpty()) {
                    Snackbar.make(findViewById(android.R.id.content), "No private key set", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                byte[] localPrivKeyBytes;

                try {
                    localPrivKeyBytes = Base64.decode(localPrivKey, Base64.NO_WRAP);
                    if (localPrivKeyBytes == null || localPrivKeyBytes.length != 32) {
                        Snackbar.make(findViewById(android.R.id.content), "Invalid private key", Snackbar.LENGTH_LONG)
                                .setActionTextColor(Color.RED)
                                .show();
                        return false;
                    }
                } catch (IllegalArgumentException e) {
                    Snackbar.make(findViewById(android.R.id.content), "Invalid private key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                byte[] localPubKeyBytes = CryptoManager.calculatePublicKey(localPrivKeyBytes);

                byte[] remotePubKeyBytes;

                try {
                    remotePubKeyBytes = Base64.decode(remotePubKey, Base64.NO_WRAP);
                    if (remotePubKeyBytes == null || remotePubKeyBytes.length != 32) {
                        Snackbar.make(findViewById(android.R.id.content), "Invalid remote public key", Snackbar.LENGTH_LONG)
                                .setActionTextColor(Color.RED)
                                .show();
                        return false;
                    }
                } catch (IllegalArgumentException e) {
                    Snackbar.make(findViewById(android.R.id.content), "Invalid remote public key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                User user = new User(uuid, localPrivKeyBytes, localPubKeyBytes, remotePubKeyBytes, name);

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
        setContentView(R.layout.activity_edit);
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

        uuid = extra.getString("uuid");

        User user = UserStore.getUser(uuid);
        if (user == null) {
            finish();
            return;
        }

        EditText name = (EditText) findViewById(R.id.edit_name);
        EditText remotePubKey = (EditText) findViewById(R.id.edit_remote_pubkey);
        EditText localPrivKey = (EditText) findViewById(R.id.edit_local_privkey);
        TextView localPubKey = (TextView) findViewById(R.id.edit_local_pubkey);

        name.setText(user.name);
        remotePubKey.setText(user.remoteAsBase64());
        localPrivKey.setText(user.localPrivAsBase64());
        localPubKey.setText(user.localAsBase64());

        localPrivKey.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int aft )
            {}

            @Override
            public void afterTextChanged(Editable s)
            {
                byte[] localPrivKeyBytes;
                try {
                    localPrivKeyBytes = Base64.decode(s.toString(), Base64.NO_WRAP);
                    if (localPrivKeyBytes == null || localPrivKeyBytes.length != 32) {
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    return;
                }

                byte[] localPubKeyBytes = CryptoManager.calculatePublicKey(localPrivKeyBytes);


                String localPubKeyStr = Base64.encodeToString(localPubKeyBytes, Base64.NO_WRAP);
                TextView localPubKey = (TextView) findViewById(R.id.edit_local_pubkey);
                localPubKey.setText(localPubKeyStr);
            }
        });

    }
}
