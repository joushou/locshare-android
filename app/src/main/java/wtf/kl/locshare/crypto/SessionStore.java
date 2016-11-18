package wtf.kl.locshare.crypto;

public interface SessionStore {
    RootKey getRootKey();
    void setRootKey(RootKey rootKey);

    int getPreviousCounter();
    void setPreviousCounter(int previousCounter);

    ChainKey getSenderChainKey();
    void setSenderChainKey(ChainKey chainKey);

    ECKeyPair getSenderRatchetKey();
    void setSenderRatchetKey(ECKeyPair ratchetKey);

    ChainKey getReceiverChainKey(ECPublicKey senderEphemeral);
    void setReceiverChain(ECPublicKey senderEphemeral, ChainKey chainKey);

    MessageKey popMessageKey(ECPublicKey senderEphemeral, int counter);
    void setMessageKey(ECPublicKey senderEphemeral, MessageKey messageKey);

    ECKeyPair getOurBaseKey();
    void setOurBaseKey(ECKeyPair ourBaseKey);

    int getTheirOneTimeKeyID();
    void setTheirOneTimeKeyID(int theirOneTimeKeyID);

    int getTheirTemporaryKeyID();
    void setTheirTemporaryKeyID(int theirTemporaryKeyID);

    ECKeyPair getOurOneTimeKey();
    void setOurOneTimeKey(ECKeyPair ourOneTimeKey);

    boolean getHasUnacknowledgedPreKey();
    void setHasUnacknowledgedPreKey(boolean hasUnacknowledgedPreKey);

    ECPublicKey getTheirIdentity();
    void setTheirIdentity(ECPublicKey theirIdentity);

    void commit();
}
