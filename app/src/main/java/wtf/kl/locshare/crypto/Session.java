package wtf.kl.locshare.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class AliceSessionParameters {
    final ECKeyPair ourIdentity;
    final ECKeyPair ourBaseKey;
    final ECPublicKey theirIdentity;
    final ECSignedPublicKey theirTemporaryKey;
    final ECPublicKey theirOneTimeKey;

    AliceSessionParameters(ECKeyPair ourIdentity, ECKeyPair ourBaseKey, ECPublicKey theirIdentity,
                           ECSignedPublicKey theirTemporaryKey, ECPublicKey theirOneTimeKey)
    throws InvalidParameters {
        if (!theirTemporaryKey.verifySignature(theirIdentity))
            throw new InvalidParameters();

        this.ourIdentity = ourIdentity;
        this.ourBaseKey = ourBaseKey;
        this.theirIdentity = theirIdentity;
        this.theirTemporaryKey = theirTemporaryKey;
        this.theirOneTimeKey = theirOneTimeKey;
    }
}

class BobSessionParameters {
    final ECKeyPair ourIdentity;
    final ECKeyPair ourTemporaryKey;
    final ECKeyPair ourOneTimeKey;
    final ECPublicKey theirIdentity;
    final ECPublicKey theirBaseKey;

    BobSessionParameters(ECKeyPair ourIdentity, ECKeyPair ourTemporaryKey, ECKeyPair ourOneTimeKey,
                         ECPublicKey theirIdentity, ECPublicKey theirBaseKey) {
        this.ourIdentity = ourIdentity;
        this.ourTemporaryKey = ourTemporaryKey;
        this.ourOneTimeKey = ourOneTimeKey;
        this.theirIdentity = theirIdentity;
        this.theirBaseKey = theirBaseKey;
    }
}

public class Session {
    private final SessionStore store;
    private final PrivateStore privateStore;

    public Session(PrivateStore privateStore, SessionStore store) {
        this.privateStore = privateStore;
        this.store = store;
    }

    public void setup(ECPublicKey theirIdentity, SignedPreKey theirTemporaryKey,
                      PreKey theirOneTimeKey) throws InvalidParameters {
        setup(theirIdentity, theirTemporaryKey.publicKey, theirTemporaryKey.id,
                theirOneTimeKey.publicKey, theirOneTimeKey.id);
    }

    public void setup(ECPublicKey theirIdentity, ECSignedPublicKey theirTemporaryKey,
                      int theirTemporaryKeyID, ECPublicKey theirOneTimeKey, int theirOneTimeKeyID)
            throws InvalidParameters {
        ECKeyPair ourBaseKey = ECKeyPair.generate();

        AliceSessionParameters p = new AliceSessionParameters(privateStore.getOurIdentity(), ourBaseKey,
                theirIdentity, theirTemporaryKey, theirOneTimeKey);

        store.setTheirIdentity(theirIdentity);
        store.setTheirTemporaryKeyID(theirTemporaryKeyID);
        store.setTheirOneTimeKeyID(theirOneTimeKeyID);
        store.setOurBaseKey(ourBaseKey);
        store.setHasUnacknowledgedPreKey(true);

        initializeSession(p);
    }

    private synchronized ChainKey getChainKey(ECPublicKey theirEphemeral) {
        ChainKey chainKey = store.getReceiverChainKey(theirEphemeral);
        if (chainKey != null)
            return chainKey;

        RootKey rootKey = store.getRootKey();
        ECKeyPair ourEphemeral = store.getSenderRatchetKey();
        Pair<RootKey, ChainKey> receiverChain = rootKey.createChain(theirEphemeral, ourEphemeral);
        ECKeyPair ourNewEphemeral = ECKeyPair.generate();
        Pair<RootKey, ChainKey> senderChain = receiverChain.first.createChain(theirEphemeral,
                ourNewEphemeral);

        store.setReceiverChain(theirEphemeral, receiverChain.second);
        store.setRootKey(senderChain.first);
        store.setPreviousCounter(Math.max(store.getSenderChainKey().getIndex()-1, 0));
        store.setSenderRatchetKey(ourNewEphemeral);
        store.setSenderChainKey(senderChain.second);

        return receiverChain.second;
    }

    private synchronized MessageKey getMessageKeys(ECPublicKey theirEphemeral, ChainKey chainKey,
                                                   int counter) throws InvalidMessage {
        if (chainKey.getIndex() > counter) {
            return store.popMessageKey(theirEphemeral, counter);
        }

        if (counter - chainKey.getIndex() > 1024)
            throw new InvalidMessage();

        while (chainKey.getIndex() < counter) {
            MessageKey messageKey = chainKey.getMessageKey();
            store.setMessageKey(theirEphemeral, messageKey);
            chainKey = chainKey.nextChainKey();
        }

        store.setReceiverChain(theirEphemeral, chainKey.nextChainKey());
        return chainKey.getMessageKey();
    }


    private synchronized void initializeSession(AliceSessionParameters p) {
        try {
            ECKeyPair senderRatchet = ECKeyPair.generate();

            ByteArrayOutputStream secrets = new ByteArrayOutputStream();
            secrets.write(getDiscontinuityBytes());
            secrets.write(p.ourIdentity.scalarMult(p.theirTemporaryKey));
            secrets.write(p.ourBaseKey.scalarMult(p.theirIdentity));
            secrets.write(p.ourBaseKey.scalarMult(p.theirTemporaryKey));

            if (p.theirOneTimeKey != null)
                secrets.write(p.ourBaseKey.scalarMult(p.theirOneTimeKey));

            Pair<RootKey, ChainKey> keys = deriveKeys(secrets.toByteArray());
            store.setReceiverChain(p.theirTemporaryKey, keys.second);

            keys = keys.first.createChain(p.theirTemporaryKey, senderRatchet);

            store.setSenderRatchetKey(senderRatchet);
            store.setSenderChainKey(keys.second);
            store.setRootKey(keys.first);
            store.commit();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private synchronized void initializeSession(BobSessionParameters p) {
        try {
            ByteArrayOutputStream secrets = new ByteArrayOutputStream();
            secrets.write(getDiscontinuityBytes());
            secrets.write(p.ourTemporaryKey.scalarMult(p.theirIdentity));
            secrets.write(p.ourIdentity.scalarMult(p.theirBaseKey));
            secrets.write(p.ourTemporaryKey.scalarMult(p.theirBaseKey));
            if (p.ourOneTimeKey != null) {
                secrets.write(p.ourOneTimeKey.scalarMult(p.theirBaseKey));
            }

            Pair<RootKey, ChainKey> keys = deriveKeys(secrets.toByteArray());

            store.setTheirIdentity(p.theirIdentity);
            store.setSenderRatchetKey(p.ourTemporaryKey);
            store.setSenderChainKey(keys.second);
            store.setRootKey(keys.first);
            store.commit();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public synchronized byte[] encrypt(byte[] message) {
        ChainKey chainKey = store.getSenderChainKey();
        MessageKey keys = chainKey.getMessageKey();
        byte[] cipherText = getCipherText(keys, message);

        Message msg = Message.create(keys.getMacKey(), store.getSenderRatchetKey().asPublicKey(),
                chainKey.getIndex(), store.getPreviousCounter(), cipherText,
                privateStore.getOurIdentity().asPublicKey(), store.getTheirIdentity());
        store.setSenderChainKey(chainKey.nextChainKey());
        store.commit();

        if (!store.getHasUnacknowledgedPreKey())
            return msg.serialized;

        PreKeyMessage pmsg = PreKeyMessage.create(store.getTheirOneTimeKeyID(),
                store.getTheirTemporaryKeyID(), store.getOurBaseKey().asPublicKey(),
                privateStore.getOurIdentity().asPublicKey(), msg);

        return pmsg.serialized;
    }

    public synchronized byte[] decrypt(byte[] cipherText) throws InvalidMessage {
        Message message;
        if (PreKeyMessage.isPreKeyMessage(cipherText)) {
            PreKeyMessage pmsg = PreKeyMessage.deserialize(cipherText);

            if (store.getTheirIdentity() != null &&
                    !store.getTheirIdentity().equals(pmsg.identityKey))
                throw new InvalidMessage();

            ECKeyPair oneTimeKey = store.getOurOneTimeKey();
            if (oneTimeKey == null) {
                store.setOurOneTimeKey(privateStore.getOneTimeKey(pmsg.oneTimeKeyID));
                privateStore.removeOneTimeKey(pmsg.oneTimeKeyID);
                oneTimeKey = store.getOurOneTimeKey();
                privateStore.commit();
            }
            ECKeyPair temporaryKey = privateStore.getTemporaryKey(pmsg.temporaryKeyID);
            if (temporaryKey == null)
                throw new InvalidMessage();

            ECKeyPair identity = privateStore.getOurIdentity();

            initializeSession(new BobSessionParameters(identity, temporaryKey, oneTimeKey,
                    pmsg.identityKey, pmsg.baseKey));

            message = pmsg.message;
        } else if (Message.isMessage(cipherText)) {
            message = Message.deserialize(cipherText);
            store.setOurOneTimeKey(null);
        } else {
            throw new InvalidMessage();
        }

        ChainKey chainKey = getChainKey(message.senderRatchetKey);
        MessageKey keys = getMessageKeys(message.senderRatchetKey, chainKey, message.counter);

        if (keys == null)
            throw new InvalidMessage();

        if (!message.verifyMac(keys.getMacKey(), store.getTheirIdentity(),
                privateStore.getOurIdentity().asPublicKey()))
            throw new InvalidMessage();

        store.setHasUnacknowledgedPreKey(false);
        store.setTheirOneTimeKeyID(0);
        store.setTheirOneTimeKeyID(0);
        store.setOurBaseKey(null);
        store.commit();

        return getPlainText(keys, message.cipherText);
    }

    private static byte[] getCipherText(MessageKey messageKey, byte[] plainText) {
        try {
            SecretKeySpec s = new SecretKeySpec(messageKey.getKey(), "AES");
            IvParameterSpec i = new IvParameterSpec(messageKey.getIV());
            Cipher cipher = getCipher(Cipher.ENCRYPT_MODE,
                    s,
                    i);
            return cipher.doFinal(plainText);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] getPlainText(MessageKey messageKey, byte[] cipherText) {
        try {
            Cipher cipher = getCipher(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(messageKey.getKey(), "AES"),
                    new IvParameterSpec(messageKey.getIV()));
            return cipher.doFinal(cipherText);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Cipher getCipher(int mode, SecretKeySpec key, IvParameterSpec iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode, key, iv);
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] getDiscontinuityBytes() {
        byte[] discontinuity = new byte[32];
        Arrays.fill(discontinuity, (byte) 0xFF);
        return discontinuity;
    }

    private Pair<RootKey, ChainKey> deriveKeys(byte[] secret) {
        byte[] expansion = HKDF.deriveSecrets(secret, "WhisperText".getBytes(), 64);
        RootKey rootKey = RootKey.fromDerivedSecret(expansion);
        ChainKey chainKey = ChainKey.fromDerivedSecret(expansion);
        return new Pair<>(rootKey, chainKey);
    }
}
