package wtf.kl.locshare.crypto;

import android.util.Base64;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import java.util.Arrays;

public class ECKeyPair {
    private static final Curve25519 cipher = Curve25519.getInstance(Curve25519.BEST);
    private static final byte[] G = {9,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};


    public final byte[] privateKey;
    public final byte[] publicKey;

    public boolean equals(ECKeyPair other) {
        return Arrays.equals(privateKey, other.privateKey);
    }

    public boolean equals(ECPublicKey other) {
        return Arrays.equals(publicKey, other.publicKey);
    }

    public ECKeyPair(byte[] privateKey) {
        if (privateKey.length != 32)
            throw new IllegalArgumentException("private key length incorrect");

        this.privateKey = privateKey;
        this.publicKey = cipher.calculateAgreement(G, privateKey);
    }

    public ECKeyPair(byte[] privateKey, byte[] publicKey) {
        if (privateKey.length != 32)
            throw new IllegalArgumentException("private key length incorrect");

        if (publicKey.length != 32)
            throw new IllegalArgumentException("public key length incorrect");

        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public byte[] scalarMult(ECPublicKey publicKey) {
        return cipher.calculateAgreement(publicKey.publicKey, this.privateKey);
    }

    public ECPublicKey asPublicKey() {
        return new ECPublicKey(publicKey);
    }

    public static ECKeyPair generate() {
        Curve25519KeyPair kp = cipher.generateKeyPair();
        return new ECKeyPair(kp.getPrivateKey(), kp.getPublicKey());
    }

    public String base64() {
        return Base64.encodeToString(privateKey, Base64.NO_WRAP);
    }

    public static ECKeyPair fromBase64(String base64) {
        return new ECKeyPair(Base64.decode(base64, Base64.NO_WRAP));
    }
}
