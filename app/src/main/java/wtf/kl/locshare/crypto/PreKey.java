package wtf.kl.locshare.crypto;

public class PreKey {
    public final ECPublicKey publicKey;
    public final int id;

    public PreKey(ECPublicKey key, int id) {
        this.publicKey = key;
        this.id = id;
    }
}
