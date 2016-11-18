package wtf.kl.locshare.crypto;

import android.util.Base64;

import org.whispersystems.curve25519.Curve25519;

import java.util.Arrays;

public class ECSignedPublicKey extends ECPublicKey {
    public final byte[] signature;
    public final byte[] publicKeyAndSignature;
    private static final Curve25519 cipher = Curve25519.getInstance(Curve25519.BEST);


    public boolean equals(ECSignedPublicKey other) {
        return Arrays.equals(publicKey, other.publicKey);
    }

    public boolean equals(ECPublicKey other) {
        return Arrays.equals(publicKey, other.publicKey);
    }

    public boolean equals(ECKeyPair other) {
        return Arrays.equals(publicKey, other.publicKey);
    }

    public ECSignedPublicKey(byte[] publicKey, byte[] signature) {
        super(publicKey);

        if (signature.length != 64)
            throw new IllegalArgumentException("signature length incorrect");

        this.signature = signature;
        this.publicKeyAndSignature = ByteUtil.combine(publicKey, signature);
    }

    public ECSignedPublicKey(byte[] serialized) {
        super(ByteUtil.cut(serialized, 0, 32));
        this.signature = ByteUtil.cut(serialized, 32, 64);
        this.publicKeyAndSignature = serialized;
    }

    public boolean verifySignature(ECPublicKey signatureKey) {
        boolean res = cipher.verifySignature(signatureKey.publicKey, this.publicKey, this.signature);
        return res;

    }

    public static ECSignedPublicKey sign(ECPublicKey source, ECKeyPair key) {
        byte[] signature = cipher.calculateSignature(key.privateKey, source.publicKey);
        return new ECSignedPublicKey(source.publicKey, signature);
    }

    public String base64() {
        return Base64.encodeToString(publicKeyAndSignature, Base64.NO_WRAP);
    }

    public static ECSignedPublicKey fromBase64(String base64) {
        byte[] p = Base64.decode(base64, Base64.NO_WRAP);
        return new ECSignedPublicKey(ByteUtil.cut(p, 0, 32), ByteUtil.cut(p, 32, 64));
    }
}
