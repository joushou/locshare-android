package wtf.kl.locshare;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;


class CryptoManager {
    private static final byte[] G = {9,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private static final Curve25519 cipher = Curve25519.getInstance(Curve25519.BEST);

    static public byte[] encryptWithCurve25519PublicKey(byte[] plainText, byte[] publicKey) {
        // K_B = publicKey;
        // m = plainText;

        SecureRandom sr = new SecureRandom();
        byte[] r = new byte[32];
        sr.nextBytes(r);
        r[0] &= 248;
        r[31] &= 127;
        r[31] |= 64;

        byte[] R = cipher.calculateAgreement(G, r); // R = rG;
        byte[] S = cipher.calculateAgreement(publicKey, r); // S = rK_B = rk_BG = k_BrG = k_BR;

        // k_E = H(S);
        byte[] k_E;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(S);
            k_E = md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(String.format("Unexpected NoSuchAlgorithm exception: %s", e));
        }

        if (plainText.length > k_E.length) {
            throw new AssertionError(String.format("plainText too long: %d > %d", plainText.length, k_E.length));
        }

        byte[] cipherText = new byte[r.length + plainText.length];

        // cipherText = R||E(k_E;plainText)
        System.arraycopy(R, 0, cipherText, 0, R.length);
        for (int i = 0; i < plainText.length; i++)
            cipherText[i+R.length] = (byte)(plainText[i] ^ k_E[i]);

        return cipherText;
    }

    static public byte[] decryptWithCurve25519PrivateKey(byte[] cipherText, byte[] privateKey) {
        // k_B = privateKey
        // (R, m) = cipherText
        byte[] R = new byte[32];
        System.arraycopy(cipherText, 0, R, 0, 32);

        byte[] c = new byte[cipherText.length - 32];
        System.arraycopy(cipherText, 32, c, 0, c.length);

        // S = k_BR
        byte[] S = cipher.calculateAgreement(R, privateKey);

        // k_E = H(S);
        byte[] k_E;
        try {
            MessageDigest md;
            md = MessageDigest.getInstance("SHA-512");
            md.update(S);
            k_E = md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(String.format("Unexpected NoSuchAlgorithm exception: %s", e));
        }

        // plainText = E(k_R;m)
        byte[] plainText = new byte[c.length];
        for (int i = 0; i < c.length; i++)
            plainText[i] = (byte)(c[i] ^ k_E[i]);

        return plainText;
    }

    static public byte[] calculatePublicKey(byte[] privateKey) {
        return cipher.calculateAgreement(G, privateKey);
    }

    static public Curve25519KeyPair generateCurve25519KeyPair() {
        return cipher.generateKeyPair();
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }
}
