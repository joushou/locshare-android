package wtf.kl.locshare.crypto;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.security.InvalidKeyException;
import java.util.Arrays;

class PreKeyMessage {
    static final byte MESSAGE_TYPE = 2;
    private static final byte MESSAGE_VERSION = 1;

    final int oneTimeKeyID;
    final int temporaryKeyID;
    final ECPublicKey baseKey;
    final ECPublicKey identityKey;
    final Message message;
    final byte[] serialized;

    private PreKeyMessage(int oneTimeKeyID, int temporaryKeyID, ECPublicKey baseKey,
                          ECPublicKey identityKey, Message message, byte[] serialized) {
        this.oneTimeKeyID = oneTimeKeyID;
        this.temporaryKeyID = temporaryKeyID;
        this.baseKey = baseKey;
        this.identityKey = identityKey;
        this.message = message;
        this.serialized = serialized;
    }

    static PreKeyMessage deserialize(byte[] serialized) throws InvalidMessage{
        ByteBuffer buffer = ByteBuffer.wrap(serialized);
        byte messageType = buffer.get();

        if (messageType != MESSAGE_TYPE)
            throw new InvalidMessage();

        byte messageVersion = buffer.get();
        if (messageVersion != MESSAGE_VERSION)
            throw new InvalidMessage();

        int oneTimeKeyID = buffer.getInt();
        int temporaryKeyID = buffer.getInt();
        ECPublicKey baseKey = new ECPublicKey(ByteUtil.cut(buffer, 32));
        ECPublicKey identityKey = new ECPublicKey(ByteUtil.cut(buffer, 32));
        Message message = Message.deserialize(ByteUtil.cut(buffer, buffer.remaining()));

        return new PreKeyMessage(oneTimeKeyID, temporaryKeyID, baseKey, identityKey, message,
                serialized);
    }

    static PreKeyMessage create(int preKeyID, int signedPreKeyID, ECPublicKey baseKey,
                                ECPublicKey identityKey, Message message) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 4 + 4 + 32 + 32 +
                message.serialized.length);
        buffer.put(MESSAGE_TYPE);
        buffer.put(MESSAGE_VERSION);
        buffer.putInt(preKeyID);
        buffer.putInt(signedPreKeyID);
        buffer.put(baseKey.publicKey);
        buffer.put(identityKey.publicKey);
        buffer.put(message.serialized);

        return new PreKeyMessage(preKeyID, signedPreKeyID, baseKey, identityKey, message,
                buffer.array());
    }

    static boolean isPreKeyMessage(byte[] serialized) {
        return serialized[0] == MESSAGE_TYPE;
    }
}

class Message {
    static final byte MESSAGE_TYPE = 1;
    private static final byte MESSAGE_VERSION = 1;
    private static final int MAC_LENGTH = 8;
    private static final byte MESSAGE_OVERHEAD = 1 + 1 + 32 + 4 + 4 + MAC_LENGTH;

    final ECPublicKey senderRatchetKey;
    final int counter;
    final int previousCounter;
    final byte[] cipherText;
    final byte[] mac;
    final byte[] serialized;

    private Message(ECPublicKey senderRatchetKey, int counter, int previousCounter,
                    byte[] cipherText, byte[] mac, byte[] serialized) {
        this.senderRatchetKey = senderRatchetKey;
        this.counter = counter;
        this.previousCounter = previousCounter;
        this.cipherText = cipherText;
        this.mac = mac;
        this.serialized = serialized;
    }

    static Message deserialize(byte[] serialized) throws InvalidMessage {
        ByteBuffer buffer = ByteBuffer.wrap(serialized);
        byte messageType = buffer.get();

        if (messageType != MESSAGE_TYPE) {
            throw new InvalidMessage();
        }

        byte messageVersion = buffer.get();
        if (messageVersion != MESSAGE_VERSION)
            throw new InvalidMessage();

        ECPublicKey senderRatchetKey = new ECPublicKey(ByteUtil.cut(buffer, 32));
        int counter = buffer.getInt();
        int previousCounter = buffer.getInt();
        byte[] cipherText = ByteUtil.cut(buffer, serialized.length - MESSAGE_OVERHEAD);
        byte[] mac = ByteUtil.cut(buffer, MAC_LENGTH);

        return new Message(senderRatchetKey, counter, previousCounter,
                cipherText, mac, serialized);
    }

    static Message create(byte[] macKey, ECPublicKey senderRatchetKey, int counter,
                                 int previousCounter, byte[] cipherText, ECPublicKey senderIdentity,
                                 ECPublicKey receiverIdentity) {

        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 32 + 4 + 4 + cipherText.length);
        buffer.put(MESSAGE_TYPE);
        buffer.put(MESSAGE_VERSION);
        buffer.put(senderRatchetKey.publicKey);
        buffer.putInt(counter);
        buffer.putInt(previousCounter);
        buffer.put(cipherText);

        byte[] firstPart = buffer.array();

        byte[] mac = calculateMac(macKey, senderIdentity, receiverIdentity, firstPart);

        return new Message(senderRatchetKey, counter, previousCounter, cipherText, mac,
                ByteUtil.combine(firstPart, mac));
    }

    boolean verifyMac(byte[] macKey, ECPublicKey senderIdentity,
                                    ECPublicKey receiverIdentity) {
        byte[] payload = ByteUtil.cut(serialized, 0, serialized.length - MAC_LENGTH);
        return Arrays.equals(mac, calculateMac(macKey, senderIdentity, receiverIdentity, payload));
    }

    private static byte[] calculateMac(byte[] macKey, ECPublicKey senderIdentity,
                                ECPublicKey receiverIdentity, byte[] payload) {
        Mac mac;

        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
        } catch (NoSuchAlgorithmException|InvalidKeyException e) {
            throw new AssertionError(e);
        }

        mac.update(senderIdentity.publicKey);
        mac.update(receiverIdentity.publicKey);
        byte[] res = new byte[MAC_LENGTH];
        System.arraycopy(mac.doFinal(payload), 0, res, 0, MAC_LENGTH);
        return res;
    }

    static boolean isMessage(byte[] serialized) {
        return serialized[0] == MESSAGE_TYPE;
    }
}
