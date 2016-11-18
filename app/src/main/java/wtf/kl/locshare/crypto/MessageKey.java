package wtf.kl.locshare.crypto;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;

public class MessageKey {
    private static final int KEY_LENGTH = 32;
    private static final int MAC_KEY_LENGTH = 32;
    private static final int IV_LENGTH = 16;

    private final byte[] key;
    private final byte[] macKey;
    private final byte[] iv;
    private final int counter;

    public MessageKey(byte[] key, byte[] macKey, byte[] iv, int counter)
            throws InvalidKeyException {
        if (key.length != KEY_LENGTH || macKey.length != MAC_KEY_LENGTH || iv.length != IV_LENGTH)
            throw new InvalidKeyException("key too short");

        this.key = key;
        this.macKey = macKey;
        this.iv = iv;
        this.counter = counter;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getMacKey() {
        return macKey;
    }

    public byte[] getIV() {
        return iv;
    }

    public int getCounter() {
        return counter;
    }

    public byte[] serialize() {
        ByteBuffer buf = ByteBuffer.allocate(KEY_LENGTH + MAC_KEY_LENGTH + IV_LENGTH + 4);
        buf.put(key);
        buf.put(macKey);
        buf.put(iv);
        buf.putInt(counter);
        return buf.array();
    }

    public String base64() {
        return Base64.encodeToString(serialize(), Base64.NO_WRAP);
    }

    public static MessageKey unserialize(byte[] b) {
        ByteBuffer buf = ByteBuffer.wrap(b);
        byte[] key = ByteUtil.cut(buf, KEY_LENGTH);
        byte[] macKey = ByteUtil.cut(buf, MAC_KEY_LENGTH);
        byte[] iv = ByteUtil.cut(buf, IV_LENGTH);
        int counter = buf.getInt();
        try {
            return new MessageKey(key, macKey, iv, counter);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public static MessageKey fromBase64(String base64) {
        return unserialize(Base64.decode(base64, Base64.NO_WRAP));
    }
}
