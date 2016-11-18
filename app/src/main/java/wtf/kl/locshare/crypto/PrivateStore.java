package wtf.kl.locshare.crypto;

public interface PrivateStore {
    ECKeyPair getOurIdentity();
    void setOurIdentity(ECKeyPair identity);

    ECKeyPair getOneTimeKey(int oneTimeKeyID);
    void removeOneTimeKey(int oneTimeKeyID);
    int[] getOneTimeKeyIDs();

    ECKeyPair getTemporaryKey(int temporaryKeyID);
    void removeTemporaryKey(int temporaryKeyID);
    int[] getTemporaryKeyIDs();

    void commit();
}
