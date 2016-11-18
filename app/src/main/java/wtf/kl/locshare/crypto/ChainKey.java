package wtf.kl.locshare.crypto;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ChainKey {
    private static final byte[] MESSAGE_KEY_SEED = {0x01};
    private static final byte[] CHAIN_KEY_SEED = {0x02};

    private final byte[] key;
    private final int index;
    private MessageKey messageKey;

    public ChainKey(byte[] key, int index) {
        this.key = key;
        this.index = index;
        messageKey = calculateMessageKeys();
    }

    public ChainKey nextChainKey() {
        byte[] nextKey = getBaseMaterial(CHAIN_KEY_SEED);
        return new ChainKey(nextKey, index + 1);
    }

    private MessageKey calculateMessageKeys() {
        byte[] inputKeyMaterial = getBaseMaterial(MESSAGE_KEY_SEED);
        byte[] keyMaterialBytes = HKDF.deriveSecrets(inputKeyMaterial, "WhisperMessageKeys".getBytes(), 80);
        byte[] cipherKey = ByteUtil.cut(keyMaterialBytes, 0, 32);
        byte[] macKey = ByteUtil.cut(keyMaterialBytes, 32, 32);
        byte[] iv = ByteUtil.cut(keyMaterialBytes, 64, 16);

        try {
            return new MessageKey(cipherKey, macKey, iv, index);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public byte[] getKey() {
        return key;
    }

    public int getIndex() {
        return index;
    }

    public MessageKey getMessageKey() {
        return messageKey;
    }

    private byte[] getBaseMaterial(byte[] seed) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));

            return mac.doFinal(seed);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public static ChainKey fromDerivedSecret(byte[] secret) {
        return new ChainKey(ByteUtil.cut(secret, 32, 32), 0);
    }

    public byte[] serialize() {
        ByteBuffer buf = ByteBuffer.allocate(36);
        buf.put(key);
        buf.putInt(index);
        return buf.array();
    }

    public String base64() {
        return Base64.encodeToString(serialize(), Base64.NO_WRAP);
    }

    public static ChainKey unserialize(byte[] b) {
        ByteBuffer buf = ByteBuffer.wrap(b);
        byte[] key = ByteUtil.cut(buf, 32);
        int index = buf.getInt();
        return new ChainKey(key, index);
    }

    public static ChainKey fromBase64(String base64) {
        return unserialize(Base64.decode(base64, Base64.NO_WRAP));
    }
}
