package wtf.kl.locshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.AsyncTask;
import android.support.design.widget.TextInputEditText;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import java.io.IOException;

import wtf.kl.locshare.crypto.ECPublicKey;
import wtf.kl.locshare.crypto.PreKey;
import wtf.kl.locshare.crypto.SignedPreKey;

public class AddUserAction {
    static private class Result {
        boolean success = false;
        boolean toast = false;
        String username = "";
        String errorMessage = "";

        static Result toast(String msg) {
            Result r = new Result();
            r.success = false;
            r.toast = true;
            r.errorMessage = msg;
            return r;
        }

        static Result success(String username) {
            Result r = new Result();
            r.username = username;
            r.success = true;
            return r;
        }
    }
    static private class AddUserTask extends AsyncTask<String, Void, Result> {
        private final Activity context;

        AddUserTask(Activity context) { this.context = context; }

        protected Result doInBackground(String ...params) {
            if (params.length != 1)
                throw new IllegalArgumentException("AddUserTask takes exactly one argument");

            UsersStore users = Storage.getInstance().getUsersStore();

            String username = params[0];
            String name;
            ECPublicKey theirIdentity;
            PreKey theirOneTimeKey;
            SignedPreKey theirTemporaryKey;

            try {
                theirIdentity = Client.getIdentity(username);
                theirOneTimeKey = Client.getOneTimeKey(username);
                theirTemporaryKey = Client.getTemporaryKey(username);
            } catch (Client.AuthException e) {
                return Result.toast("Please log in to add users");
            } catch (Client.ClientException e) {
                e.printStackTrace();
                return Result.toast("Could not retrieve information about user");
            } catch (Client.ServerException e) {
                e.printStackTrace();
                return Result.toast("Server error occurred");
            } catch (IOException e) {
                e.printStackTrace();
                return Result.toast("Network error occurred");
            } catch (Exception e) {
                e.printStackTrace();
                return Result.toast("Unexpected error occurred");
            }

            try {
                User user = users.newUser(username);
                user.setPublish(true);
                user.getSession().setup(theirIdentity, theirTemporaryKey, theirOneTimeKey);
                users.addUser(user);
                user.store();
            } catch (Exception e) {
                e.printStackTrace();
                return Result.toast("Unexpected error occurred");
            }

            return Result.success(username);
        }

        protected void onPostExecute(Result result) {
            if (result.success) {
                Storage.getInstance().getUsersStore().notifyUpdate(result.username);
                return;
            }

            String msg = result.errorMessage;
            if (result.toast) {
                Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    static public void build(Activity context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Add user");

        View view = context.getLayoutInflater().inflate(R.layout.alertdialog_add_user, null);
        builder.setView(view);

        TextInputEditText username = (TextInputEditText) view.findViewById(R.id.add_user_username);

        builder.setPositiveButton("Add",(dialog, which) -> {
            new AddUserTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    username.getText().toString());
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
        });

        AlertDialog dialog = builder.show();

        username.setOnEditorActionListener((v, actionId, event) -> {
            switch (actionId) {
                case EditorInfo.IME_ACTION_DONE:
                    dialog.cancel();
                    new AddUserTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                            username.getText().toString());
                    return true;
            }
            return false;
        });

    }
}
