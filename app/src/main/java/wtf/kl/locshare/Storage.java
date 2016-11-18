package wtf.kl.locshare;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import wtf.kl.locshare.crypto.ChainKey;
import wtf.kl.locshare.crypto.ECKeyPair;
import wtf.kl.locshare.crypto.ECPublicKey;
import wtf.kl.locshare.crypto.MessageKey;
import wtf.kl.locshare.crypto.RootKey;
import wtf.kl.locshare.crypto.Session;

class StorageUtils {
    public static byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] b = new byte[(int)file.length()];
            fis.read(b);
            return b;
        }
    }

    public static void writeToFile(File file, byte[] b) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(b);
            fos.flush();
        }
    }

    public static boolean delete(File file) {
        if (file.isDirectory()) {
            for (File c : file.listFiles())
                delete(c);
        }
        return file.delete();
    }
}

class Storage {
    private static Storage ourInstance = null;
    public static void createInstance(Context context) {
        if (ourInstance == null)
            ourInstance = new Storage(context.getFilesDir());
    }
    public static Storage getInstance() {
        return ourInstance;
    }

    private UsersStore usersStore;
    private PrivateStore privateStore;

    private Storage(File directory) {
        this.usersStore = new UsersStore(new File(directory, "users"));
        this.privateStore = new PrivateStore(new File(directory, "private"));
    }

    public void load() throws Exception {
        this.usersStore.load();
        this.privateStore.load();
    }

    public void store() throws Exception {
        this.privateStore.store();
        this.usersStore.store();
    }

    public UsersStore getUsersStore() {
        return usersStore;
    }

    public PrivateStore getPrivateStore() {
        return privateStore;
    }
}

class PrivateStore implements wtf.kl.locshare.crypto.PrivateStore {
    private ECKeyPair ourIdentity;
    private Map<Integer, ECKeyPair> ourOneTimeKeys;
    private Map<Integer, ECKeyPair> ourTemporaryKeys;
    private File directory;
    private boolean dirty;

    PrivateStore(File directory) {
        this.directory = directory;
        this.ourIdentity = null;
        this.ourOneTimeKeys = new HashMap<>();
        this.ourTemporaryKeys = new HashMap<>();
        this.dirty = true;
    }

    public void commit() {
        try {
            store();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void load() throws IOException {
        File ourIdentityFile = new File(this.directory, "ourIdentity");
        File ourOneTimeKeysFile = new File(this.directory, "ourOneTimeKeys");
        File ourTemporaryKeysFile = new File(this.directory, "ourTemporaryKeys");

        byte[] ourIdentityBytes = StorageUtils.readFile(ourIdentityFile);
        byte[] ourOneTimeKeysBytes = StorageUtils.readFile(ourOneTimeKeysFile);
        byte[] ourTemporaryKeysBytes = StorageUtils.readFile(ourTemporaryKeysFile);

        this.ourIdentity = null;
        this.ourOneTimeKeys = new HashMap<>();
        this.ourTemporaryKeys = new HashMap<>();

        if (ourIdentityBytes.length == 32)
            this.ourIdentity = new ECKeyPair(ourIdentityBytes);

        try {

            JSONArray ourOneTimeKeysArr = new JSONArray(new String(ourOneTimeKeysBytes,
                    StandardCharsets.UTF_8));
            Map<Integer, ECKeyPair> ourOneTimeKeys = new HashMap<>();
            for (int i = 0; i < ourOneTimeKeysArr.length(); i++) {
                JSONArray arr = ourOneTimeKeysArr.getJSONArray(i);
                ourOneTimeKeys.put(arr.getInt(0), ECKeyPair.fromBase64(arr.getString(1)));
            }

            JSONArray ourTemporaryKeysArr = new JSONArray(new String(ourTemporaryKeysBytes,
                    StandardCharsets.UTF_8));

            Map<Integer, ECKeyPair> ourTemporaryKeys = new HashMap<>();
            for (int i = 0; i < ourTemporaryKeysArr.length(); i++) {
                JSONArray arr = ourTemporaryKeysArr.getJSONArray(i);
                ourTemporaryKeys.put(arr.getInt(0), ECKeyPair.fromBase64(arr.getString(1)));
            }

            this.ourOneTimeKeys = ourOneTimeKeys;
            this.ourTemporaryKeys = ourTemporaryKeys;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        this.dirty = false;
    }

    public synchronized void store() throws IOException {
        if (!this.dirty)
            return;

        this.directory.mkdirs();

        File ourIdentityFile = new File(this.directory, "ourIdentity");
        File ourOneTimeKeysFile = new File(this.directory, "ourOneTimeKeys");
        File ourTemporaryKeysFile = new File(this.directory, "ourTemporaryKeys");

        ourIdentityFile.createNewFile();
        ourOneTimeKeysFile.createNewFile();
        ourTemporaryKeysFile.createNewFile();

        if (this.ourIdentity != null) {
            StorageUtils.writeToFile(ourIdentityFile, this.ourIdentity.privateKey);
        }

        JSONArray ourOneTimeKeysArr = new JSONArray();
        if (this.ourOneTimeKeys != null) {
            for (Map.Entry<Integer, ECKeyPair> entry : this.ourOneTimeKeys.entrySet()) {
                JSONArray arr = new JSONArray();
                arr.put(entry.getKey());
                arr.put(entry.getValue().base64());
                ourOneTimeKeysArr.put(arr);
            }
        }

        JSONArray ourTemporaryKeysArr = new JSONArray();
        if (this.ourTemporaryKeys != null) {
            for (Map.Entry<Integer, ECKeyPair> entry : this.ourTemporaryKeys.entrySet()) {
                JSONArray arr = new JSONArray();
                arr.put(entry.getKey());
                arr.put(entry.getValue().base64());
                ourTemporaryKeysArr.put(arr);
            }
        }

        StorageUtils.writeToFile(ourOneTimeKeysFile,
                ourOneTimeKeysArr.toString().getBytes(StandardCharsets.UTF_8));
        StorageUtils.writeToFile(ourTemporaryKeysFile,
                ourTemporaryKeysArr.toString().getBytes(StandardCharsets.UTF_8));

        this.dirty = false;
    }

    public synchronized void delete() {
        StorageUtils.delete(this.directory);
    }

    public ECKeyPair getOurIdentity() {
        return ourIdentity;
    }

    public synchronized void setOurIdentity(ECKeyPair ourIdentity) {
        this.ourIdentity = ourIdentity;
        this.dirty = true;
    }

    public synchronized ECKeyPair getOneTimeKey(int oneTimeKeyID) {
        return ourOneTimeKeys.get(oneTimeKeyID);
    }

    public synchronized void setOneTimeKey(int oneTimeKeyID, ECKeyPair key) {
        ourOneTimeKeys.put(oneTimeKeyID, key);
        this.dirty = true;
    }

    public synchronized void removeOneTimeKey(int oneTimeKeyID) {
        ourOneTimeKeys.remove(oneTimeKeyID);
        this.dirty = true;
    }

    public synchronized int[] getOneTimeKeyIDs() {
        Integer[] arr = (Integer[])ourOneTimeKeys.keySet().toArray();
        int[] arr2 = new int[arr.length];              // I
        System.arraycopy(arr, 0, arr2, 0, arr.length); // Hate
        return arr2;                                   // Java
    }

    public synchronized ECKeyPair getTemporaryKey(int temporaryKeyID) {
        return ourTemporaryKeys.get(temporaryKeyID);
    }

    public synchronized void setTemporaryKey(int temporaryKeyID, ECKeyPair key) {
        ourTemporaryKeys.put(temporaryKeyID, key);
        this.dirty = true;
    }

    public synchronized void removeTemporaryKey(int temporaryKeyID) {
        ourTemporaryKeys.remove(temporaryKeyID);
        this.dirty = true;
    }

    public synchronized int[] getTemporaryKeyIDs() {
        Integer[] arr = (Integer[])ourTemporaryKeys.keySet().toArray();
        int[] arr2 = new int[arr.length];              // I
        System.arraycopy(arr, 0, arr2, 0, arr.length); // Hate
        return arr2;                                   // Java
    }
}

class ChainKeyEntry {
    private static final int MAX_MESSAGE_KEYS = 1024;

    private ECPublicKey senderRatchetKey;
    private ChainKey chainKey;
    private List<MessageKey> messageKeyList;
    private File directory;
    private boolean dirty;

    ChainKeyEntry(File directory, ECPublicKey senderRatchetKey) {
        this.directory = directory;
        this.senderRatchetKey = senderRatchetKey;
        this.dirty = false;
    }

    ChainKeyEntry(File directory, ECPublicKey senderRatchetKey, ChainKey chainKey) {
        this.directory = directory;
        this.senderRatchetKey = senderRatchetKey;
        this.chainKey = chainKey;
        this.messageKeyList = new LinkedList<>();
        this.dirty = true;
    }

    synchronized void load() throws IOException {
        File senderRatchetKeyFile = new File(this.directory, "senderRatchetKey");
        File chainKeyFile = new File(this.directory, "chainKey");
        File messageKeyListFile = new File(this.directory, "messageKeyList");

        byte[] senderRatchetKeyBytes = StorageUtils.readFile(senderRatchetKeyFile);
        byte[] chainKeyBytes = StorageUtils.readFile(chainKeyFile);
        byte[] messageKeyListBytes = StorageUtils.readFile(messageKeyListFile);

        try {
            List<MessageKey> messageKeyList = new ArrayList<>();
            JSONArray arr = new JSONArray(new String(messageKeyListBytes, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                messageKeyList.add(MessageKey.fromBase64(arr.getString(i)));
            }
            this.messageKeyList = messageKeyList;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        this.senderRatchetKey = new ECPublicKey(senderRatchetKeyBytes);
        this.chainKey = ChainKey.unserialize(chainKeyBytes);
        this.dirty = false;
    }

    synchronized void store() throws IOException {
        if (!this.dirty)
            return;

        this.directory.mkdirs();

        File senderRatchetKeyFile = new File(this.directory, "senderRatchetKey");
        File chainKeyFile = new File(this.directory, "chainKey");
        File messageKeyListFile = new File(this.directory, "messageKeyList");

        senderRatchetKeyFile.createNewFile();
        chainKeyFile.createNewFile();
        messageKeyListFile.createNewFile();

        StorageUtils.writeToFile(senderRatchetKeyFile, this.senderRatchetKey.publicKey);
        StorageUtils.writeToFile(chainKeyFile, this.chainKey.serialize());

        JSONArray arr = new JSONArray();
        for (MessageKey keys : this.messageKeyList) {
            arr.put(keys.base64());
        }
        StorageUtils.writeToFile(messageKeyListFile, arr.toString().getBytes(StandardCharsets.UTF_8));

        this.dirty = false;
    }

    synchronized void delete() {
        StorageUtils.delete(this.directory);
    }

    ECPublicKey getSenderRatchetKey() {
        return this.senderRatchetKey;
    }

    void setChainKey(ChainKey chainKey) {
        this.chainKey = chainKey;
        dirty = true;
    }

    ChainKey getChainKey() {
        return this.chainKey;
    }

    synchronized MessageKey popMessageKey(int counter) {
        Iterator<MessageKey> iterator = messageKeyList.iterator();
        while (iterator.hasNext()) {
            MessageKey messageKey = iterator.next();

            if (messageKey.getCounter() == counter) {
                iterator.remove();
                dirty = true;
                return messageKey;
            }
        }
        return null;
    }

    synchronized void addMessageKey(MessageKey messageKey) {
        messageKeyList.add(messageKey);
        if (messageKeyList.size() > MAX_MESSAGE_KEYS)
            messageKeyList.remove(0);
        dirty = true;
    }

}

class SessionStore implements wtf.kl.locshare.crypto.SessionStore {
    private static final int MAX_RECEIVER_CHAINS = 5;

    private RootKey rootKey = null;
    private int previousCounter = 0;
    private ECKeyPair senderRatchetKey = null;

    private ECPublicKey theirIdentity = null;
    private int theirOneTimeKeyID = 0;
    private int theirTemporaryKeyID = 0;
    private ECKeyPair ourBaseKey = null;
    private boolean hasUnacknowledgedPreKey = false;
    private ECKeyPair ourOneTimeKey = null;

    private ChainKey senderChainKey = null;

    private List<ChainKeyEntry> receiverChain = new LinkedList<>();

    private File directory;
    private File receiverChainDirectory;
    private boolean dirtyKeys;
    private boolean dirtySenderChainKey;
    private boolean dirtySetup;

    SessionStore(File directory) {
        this.directory = directory;
        this.receiverChainDirectory = new File(directory, "receiverChain");
        this.dirtyKeys = true;
        this.dirtySenderChainKey = true;
        this.dirtySetup = true;
    }

    public void commit() {
        try {
            this.store();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void load() throws IOException {
        File senderChainKeyFile = new File(this.directory, "senderChainKey");
        File keysFile = new File(this.directory, "keys");
        File setupFile = new File(this.directory, "setup");
        File[] receiverChainDirectories = receiverChainDirectory.listFiles();

        byte[] senderChainKeyBytes = StorageUtils.readFile(senderChainKeyFile);
        byte[] keysBytes = StorageUtils.readFile(keysFile);
        byte[] setupBytes = StorageUtils.readFile(setupFile);

        this.senderChainKey = ChainKey.unserialize(senderChainKeyBytes);
        this.dirtySenderChainKey = false;
        this.rootKey = null;
        this.senderRatchetKey = null;
        this.theirIdentity = null;
        this.ourBaseKey = null;
        this.ourOneTimeKey = null;

        try {
            JSONObject keysObj = new JSONObject(new String(keysBytes, StandardCharsets.UTF_8));
            this.previousCounter = keysObj.getInt("previousCounter");
            String rootKeyString = keysObj.getString("rootKey");
            if (rootKeyString.length() != 0) {
                this.rootKey = RootKey.fromBase64(rootKeyString);
            }
            String senderRatchetKeyString = keysObj.getString("senderRatchetKey");
            if (senderRatchetKeyString.length() != 0) {
                this.senderRatchetKey = ECKeyPair.fromBase64(senderRatchetKeyString);
            }

            JSONObject setupObj = new JSONObject(new String(setupBytes, StandardCharsets.UTF_8));
            this.theirOneTimeKeyID = setupObj.getInt("theirOneTimeKeyID");
            this.theirTemporaryKeyID = setupObj.getInt("theirTemporaryKeyID");
            this.hasUnacknowledgedPreKey = setupObj.getBoolean("hasUnacknowledgedPreKey");
            String theirIdentityString = setupObj.getString("theirIdentity");
            if (theirIdentityString.length() != 0) {
                this.theirIdentity = ECPublicKey.fromBase64(theirIdentityString);
            }
            String ourBaseKeyString = setupObj.getString("ourBaseKey");
            if (ourBaseKeyString.length() != 0) {
                this.ourBaseKey = ECKeyPair.fromBase64(ourBaseKeyString);
            }
            String ourOneTimeKeyString = setupObj.getString("ourOneTimeKey");
            if (ourOneTimeKeyString.length() != 0) {
                this.ourOneTimeKey = ECKeyPair.fromBase64(ourOneTimeKeyString);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        this.dirtyKeys = false;
        this.dirtySetup = false;

        List<ChainKeyEntry> receiverChain = new LinkedList<>();
        if (receiverChainDirectories != null) {
            for (File receiverChainDirectory : receiverChainDirectories) {
                ECPublicKey senderEphemeralKey = new ECPublicKey(Base64.decode(receiverChainDirectory.getName(),
                        Base64.NO_WRAP | Base64.URL_SAFE));

                System.err.println("Loading chainKeyEntry: " + receiverChainDirectory.getName());
                ChainKeyEntry entry = new ChainKeyEntry(receiverChainDirectory, senderEphemeralKey);
                entry.load();
                receiverChain.add(entry);
            }
        }
        this.receiverChain = receiverChain;

    }

    public synchronized void store() throws IOException {
        for (ChainKeyEntry entry : this.receiverChain) {
            entry.store();
        }

        if (!this.dirtySenderChainKey && !this.dirtyKeys && !this.dirtySetup)
            return;

        this.directory.mkdirs();
        this.receiverChainDirectory.mkdirs();

        if (this.dirtySenderChainKey) {
            File senderChainKeyFile = new File(this.directory, "senderChainKey");
            senderChainKeyFile.createNewFile();
            StorageUtils.writeToFile(senderChainKeyFile, this.senderChainKey.serialize());
            this.dirtySenderChainKey = false;
        }

        if (this.dirtyKeys) {
            try {
                File keysFile = new File(this.directory, "keys");
                keysFile.createNewFile();

                JSONObject keysObj = new JSONObject();
                keysObj.put("previousCounter", this.previousCounter);
                if (this.rootKey == null) {
                    keysObj.put("rootKey", "");
                } else {
                    keysObj.put("rootKey", this.rootKey.base64());
                }
                if (this.senderRatchetKey == null) {
                    keysObj.put("senderRatchetKey", "");
                } else {
                    keysObj.put("senderRatchetKey", this.senderRatchetKey.base64());
                }

                StorageUtils.writeToFile(keysFile,
                        keysObj.toString().getBytes(StandardCharsets.UTF_8));
                this.dirtyKeys = false;
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }

        if (this.dirtySetup) {
            try {
                File setupFile = new File(this.directory, "setup");
                setupFile.createNewFile();
                JSONObject setupObj = new JSONObject();
                setupObj.put("theirOneTimeKeyID", this.theirOneTimeKeyID);
                setupObj.put("theirTemporaryKeyID", this.theirTemporaryKeyID);
                setupObj.put("hasUnacknowledgedPreKey", this.hasUnacknowledgedPreKey);
                if (this.theirIdentity == null) {
                    setupObj.put("theirIdentity", "");
                } else {
                    setupObj.put("theirIdentity", this.theirIdentity.base64());
                }
                if (this.ourBaseKey == null) {
                    setupObj.put("ourBaseKey", "");
                } else {
                    setupObj.put("ourBaseKey", this.ourBaseKey.base64());
                }
                if (this.ourOneTimeKey == null) {
                    setupObj.put("ourOneTimeKey", "");
                } else {
                    setupObj.put("ourOneTimeKey", this.ourOneTimeKey.base64());
                }

                StorageUtils.writeToFile(setupFile,
                        setupObj.toString().getBytes(StandardCharsets.UTF_8));
                this.dirtySetup = false;
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }
    }

    public RootKey getRootKey() {
        return rootKey;
    }

    public void setRootKey(RootKey rootKey) {
        this.rootKey = rootKey;
        this.dirtyKeys = true;
    }

    public int getPreviousCounter() {
        return previousCounter;
    }

    public void setPreviousCounter(int previousCounter) {
        this.previousCounter = previousCounter;
        this.dirtyKeys = true;
    }

    public ECKeyPair getSenderRatchetKey() {
        return senderRatchetKey;
    }

    public void setSenderRatchetKey(ECKeyPair senderRatchetKey) {
        this.senderRatchetKey = senderRatchetKey;
        this.dirtyKeys = true;
    }

    public ChainKey getSenderChainKey() {
        return senderChainKey;
    }

    public void setSenderChainKey(ChainKey senderChainKey) {
        this.senderChainKey = senderChainKey;
        this.dirtySenderChainKey = true;
    }

    public int getTheirOneTimeKeyID() {
        return theirOneTimeKeyID;
    }

    public void setTheirOneTimeKeyID(int theirOneTimeKeyID) {
        this.theirOneTimeKeyID = theirOneTimeKeyID;
        this.dirtySetup = true;
    }

    public int getTheirTemporaryKeyID() {
        return theirTemporaryKeyID;
    }

    public void setTheirTemporaryKeyID(int theirTemporaryKeyID) {
        this.theirTemporaryKeyID = theirTemporaryKeyID;
        this.dirtySetup = true;
    }

    public ECKeyPair getOurBaseKey() {
        return ourBaseKey;
    }

    public void setOurBaseKey(ECKeyPair ourBaseKey) {
        this.ourBaseKey = ourBaseKey;
        this.dirtySetup = true;
    }

    public boolean getHasUnacknowledgedPreKey() {
        return hasUnacknowledgedPreKey;
    }

    public void setHasUnacknowledgedPreKey(boolean hasUnacknowledgedPreKey) {
        this.hasUnacknowledgedPreKey = hasUnacknowledgedPreKey;
        this.dirtySetup = true;
    }

    public ECKeyPair getOurOneTimeKey() {
        return ourOneTimeKey;
    }

    public void setOurOneTimeKey(ECKeyPair ourOneTimeKey) {
        this.ourOneTimeKey = ourOneTimeKey;
        this.dirtySetup = true;
    }

    public ECPublicKey getTheirIdentity() {
        return theirIdentity;
    }

    public void setTheirIdentity(ECPublicKey theirIdentity) {
        this.theirIdentity = theirIdentity;
        this.dirtySetup = true;
    }

    private synchronized ChainKeyEntry getReceiverChainKeyEntry(ECPublicKey senderEphemeral) {
        for (int i = receiverChain.size()-1; i >= 0; i--) {
            ChainKeyEntry entry = receiverChain.get(i);
            if (entry.getSenderRatchetKey().equals(senderEphemeral))
                return entry;
        }

        return null;
    }

    public synchronized ChainKey getReceiverChainKey(ECPublicKey senderEphemeral) {
        ChainKeyEntry entry = getReceiverChainKeyEntry(senderEphemeral);
        if (entry == null)
            return null;

        return entry.getChainKey();
    }

    public synchronized void setReceiverChain(ECPublicKey senderEphemeral, ChainKey chainKey) {
        for (int i = receiverChain.size()-1; i >= 0; i--) {
            ChainKeyEntry entry = receiverChain.get(i);
            if (entry.getSenderRatchetKey().equals(senderEphemeral)) {
                entry.setChainKey(chainKey);
                return;
            }
        }

        ChainKeyEntry entry = new ChainKeyEntry(new File(receiverChainDirectory,
                Base64.encodeToString(senderEphemeral.publicKey, Base64.NO_WRAP| Base64.URL_SAFE)),
                senderEphemeral, chainKey);
        receiverChain.add(entry);

        try {
            entry.store();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (receiverChain.size() > MAX_RECEIVER_CHAINS) {
            receiverChain.remove(0).delete();
        }
    }

    public synchronized MessageKey popMessageKey(ECPublicKey senderEphemeral, int counter) {
        ChainKeyEntry entry = getReceiverChainKeyEntry(senderEphemeral);
        if (entry == null)
            return null;

        return entry.popMessageKey(counter);
    }

    public synchronized void setMessageKey(ECPublicKey senderEphemeral, MessageKey messageKey) {
        ChainKeyEntry entry = getReceiverChainKeyEntry(senderEphemeral);
        if (entry == null)
            return;

        entry.addMessageKey(messageKey);
    }
}

class UsersStore {
    interface UpdateListener {
        void onUpdate(String source);
    }

    private Map<String, User> users = new HashMap<>();
    private final ArrayList<UpdateListener> cbs = new ArrayList<>();

    private File directory;

    UsersStore(File directory) {
        this.directory = directory;
    }

    public synchronized void load() throws IOException {
        File[] userDirectories = this.directory.listFiles();
        Map<String, User> users = new HashMap<>();

        if (userDirectories != null) {
            for (File userDirectory : userDirectories) {
                String username = userDirectory.getName();

                SessionStore store = new SessionStore(new File(userDirectory, "session"));
                Session session = new Session(Storage.getInstance().getPrivateStore(), store);
                User user = new User(userDirectory, username, session);

                store.load();
                user.load();

                users.put(username, user);
            }
        }

        this.users = users;
    }

    public synchronized void store() throws IOException {
        for (Map.Entry<String, User> entry : this.users.entrySet()) {
            entry.getValue().store();
        }
    }

    public Set<String> getUserKeys() {
        return users.keySet();
    }

    public User getUser(String username) {
        return users.get(username);
    }

    public User newUser(String username) {
        File userDirectory = new File(directory, username);
        return new User(userDirectory, username,
                new Session(Storage.getInstance().getPrivateStore(),
                new SessionStore(new File(userDirectory, "session"))));
    }

    public synchronized void addUser(User user) {
        Map<String, User> u = new HashMap<>(users);
        u.put(user.getUsername(), user);
        users = u;
    }

    public synchronized void delUser(User user) {
        Map<String, User> u = new HashMap<>(users);
        u.remove(user.getUsername());
        users = u;
        user.delete();
    }

    public void addUpdateListener(UpdateListener cb) {
        cbs.add(cb);
    }

    public void delUpdateListener(UpdateListener cb) {
        cbs.remove(cb);
    }

    public void notifyUpdate(String source) {
        for (UpdateListener cb : cbs) {
            cb.onUpdate(source);
        }
    }
}
