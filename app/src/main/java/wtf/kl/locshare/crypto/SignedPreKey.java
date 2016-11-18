package wtf.kl.locshare.crypto;

public class SignedPreKey {
    public final ECSignedPublicKey publicKey;
    public final int id;

    public SignedPreKey(ECSignedPublicKey key, int id) {
        this.publicKey = key;
        this.id = id;
    }
}
