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
import android.widget.Switch;
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
                boolean publish = ((Switch)findViewById(R.id.edit_publish)).isChecked();
                boolean subscribe = ((Switch)findViewById(R.id.edit_subscribe)).isChecked();

                if (localPrivKey.isEmpty()) {
                    Snackbar.make(findViewById(android.R.id.content), "No private key set", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                User user = UserStore.getUser(uuid);
                String uuid = user.uuid;
                byte[] localPrivKeyBytes = user.localPrivKey;
                byte[] localPubKeyBytes = user.localPubKey;
                byte[] remotePubKeyBytes = user.remotePubKey;

                try {
                    byte[] p = Base64.decode(localPrivKey, Base64.NO_WRAP);

                    if (subscribe || p != null) {
                        if ((p == null) || (!(p.length == 0 && !subscribe) && p.length != 32)) {
                            Snackbar.make(findViewById(android.R.id.content), "Invalid private key", Snackbar.LENGTH_LONG)
                                    .setActionTextColor(Color.RED)
                                    .show();
                            return false;
                        }

                        localPrivKeyBytes = p;
                        localPubKeyBytes = CryptoManager.calculatePublicKey(p);
                        uuid = Base64.encodeToString(localPubKeyBytes, Base64.NO_WRAP);
                    }
                } catch (IllegalArgumentException e) {
                    Snackbar.make(findViewById(android.R.id.content), "Invalid private key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                try {
                    byte[] p = Base64.decode(remotePubKey, Base64.NO_WRAP);

                    if (publish || p != null) {
                        if ((p == null) || (!(p.length == 0 && !publish) && p.length != 32)) {
                            Snackbar.make(findViewById(android.R.id.content), "Invalid remote public key", Snackbar.LENGTH_LONG)
                                    .setActionTextColor(Color.RED)
                                    .show();
                            return false;
                        }
                        remotePubKeyBytes = p;
                    }

                } catch (IllegalArgumentException e) {
                    Snackbar.make(findViewById(android.R.id.content), "Invalid remote public key", Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.RED)
                            .show();
                    return false;
                }

                User newUser = new User(uuid, name, publish, subscribe, localPrivKeyBytes,
                        localPubKeyBytes, remotePubKeyBytes, user.cap);

                newUser.setLocationArray(user.cloneLocationArray());

                UserStore.delUser(user);
                UserStore.addUser(newUser);

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
        Switch publish = (Switch) findViewById(R.id.edit_publish);
        Switch subscribe = (Switch) findViewById(R.id.edit_subscribe);

        name.setText(user.name);
        remotePubKey.setText(user.remoteAsBase64());
        localPrivKey.setText(user.localPrivAsBase64());
        localPubKey.setText(user.localAsBase64());
        publish.setChecked(user.publish);
        subscribe.setChecked(user.subscribe);

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
