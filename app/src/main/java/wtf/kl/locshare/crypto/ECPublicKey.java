package wtf.kl.locshare.crypto;

import android.util.Base64;

import java.util.Arrays;

public class ECPublicKey {
    public final byte[] publicKey;

    public boolean equals(ECKeyPair other) { return Arrays.equals(publicKey, other.publicKey); }

    public boolean equals(ECPublicKey other) {
        return Arrays.equals(publicKey, other.publicKey);
    }

    public ECPublicKey(byte[] publicKey) {
        if (publicKey.length != 32)
            throw new IllegalArgumentException("public key length incorrect");

        this.publicKey = publicKey;
    }

    public String base64() {
        return Base64.encodeToString(publicKey, Base64.NO_WRAP);
    }

    public static ECPublicKey fromBase64(String base64) {
        return new ECPublicKey(Base64.decode(base64, Base64.NO_WRAP));
    }
}
