package wtf.kl.locshare.crypto;

import android.util.Base64;

public class RootKey {
    private final byte[] key;
    public byte[] getKey() { return key; }

    public RootKey(byte[] key) {
        this.key = key;
    }

    public Pair<RootKey, ChainKey> createChain(ECPublicKey theirRatchetKey,
                                               ECKeyPair ourRatchetKey) {
        byte[] secret = ourRatchetKey.scalarMult(theirRatchetKey);
        byte[] expansion = HKDF.deriveSecrets(secret, key, "WhisperRatchet".getBytes(), 64);
        RootKey rk = RootKey.fromDerivedSecret(expansion);
        ChainKey ck = ChainKey.fromDerivedSecret(expansion);

        return new Pair<>(rk, ck);
    }

    public static RootKey fromDerivedSecret(byte[] secret) {
        return new RootKey(ByteUtil.cut(secret, 0, 32));
    }

    public String base64() {
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }

    public static RootKey fromBase64(String base64) {
        return new RootKey(Base64.decode(base64, Base64.NO_WRAP));
    }
}
