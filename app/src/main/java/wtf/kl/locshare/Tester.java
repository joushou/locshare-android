package wtf.kl.locshare;

import java.io.File;
import java.util.Arrays;

import wtf.kl.locshare.crypto.ECKeyPair;
import wtf.kl.locshare.crypto.ECSignedPublicKey;
import wtf.kl.locshare.crypto.Session;

class Tester {
    File directory;
    void run() throws Exception {
        session_setup_test();
        session_setup_test2();
    }

    PrivateStore generatePrivateStore() {
        PrivateStore privateStore = new wtf.kl.locshare.PrivateStore(
                new File(directory, "private"));

        for (int i = 0; i < 100; i++)
            privateStore.setOneTimeKey(i, ECKeyPair.generate());

        privateStore.setTemporaryKey(0, ECKeyPair.generate());
        privateStore.setOurIdentity(ECKeyPair.generate());
        return privateStore;
    }

    void session_setup_test() throws Exception {
        PrivateStore alicePrivateStore = generatePrivateStore();
        PrivateStore bobPrivateStore = generatePrivateStore();

        ECKeyPair theirOneTimeKey = bobPrivateStore.getOneTimeKey(0);
        ECKeyPair theirTemporaryKey = bobPrivateStore.getTemporaryKey(0);
        ECSignedPublicKey theirSignedTemporaryKey = ECSignedPublicKey.sign(
                theirTemporaryKey.asPublicKey(), bobPrivateStore.getOurIdentity());

        if (!theirSignedTemporaryKey.verifySignature(bobPrivateStore.getOurIdentity().asPublicKey()))
            throw new AssertionError("crap?");


        byte[] input1 = new byte[]{1, 2, 3};

        SessionStore aliceSessionStore = new SessionStore(new File(directory, "aliceSession"));
        SessionStore bobSessionStore = new SessionStore(new File(directory, "bobSession"));
        Session aliceSession = new Session(alicePrivateStore, aliceSessionStore);
        Session bobSession = new Session(bobPrivateStore, bobSessionStore);

        aliceSession.setup(bobPrivateStore.getOurIdentity().asPublicKey(), theirSignedTemporaryKey, 0,
                theirOneTimeKey.asPublicKey(), 0);
        byte[] msg1 = aliceSession.encrypt(input1);
        byte[] msg2 = aliceSession.encrypt(input1);
        byte[] msg3 = aliceSession.encrypt(input1);

        if (Arrays.equals(msg1, msg2) || Arrays.equals(msg2, msg3) || Arrays.equals(msg1, msg3))
            throw new AssertionError("sent identical messages");

        byte[] res1 = bobSession.decrypt(msg1);
        byte[] res3 = bobSession.decrypt(msg3);
        byte[] res2 = bobSession.decrypt(msg2);
        if (
                !Arrays.equals(input1, res1) ||
                !Arrays.equals(input1, res2) ||
                !Arrays.equals(input1, res3) ||
                false)
            throw new AssertionError("result does not match expectation");

        byte[] msg4 = bobSession.encrypt(input1);
        byte[] msg5 = bobSession.encrypt(input1);
        byte[] msg6 = bobSession.encrypt(input1);
        if (
                Arrays.equals(msg4, msg5) ||
                Arrays.equals(msg5, msg6) ||
                Arrays.equals(msg4, msg6) ||
                false)
            throw new AssertionError("sent identical messages");

        byte[] res4 = aliceSession.decrypt(msg4);
        byte[] res6 = aliceSession.decrypt(msg6);
        byte[] res5 = aliceSession.decrypt(msg5);
        if (
                !Arrays.equals(input1, res4) ||
                !Arrays.equals(input1, res5) ||
                !Arrays.equals(input1, res6) ||
                false)
            throw new AssertionError("result does not match expectation");

        msg1 = aliceSession.encrypt(input1);
        msg2 = aliceSession.encrypt(input1);
        msg3 = aliceSession.encrypt(input1);

        if (Arrays.equals(msg1, msg2) || Arrays.equals(msg2, msg3) || Arrays.equals(msg1, msg3))
            throw new AssertionError("sent identical messages");

        res1 = bobSession.decrypt(msg1);
        res3 = bobSession.decrypt(msg3);
        res2 = bobSession.decrypt(msg2);
        if (
                !Arrays.equals(input1, res1) ||
                !Arrays.equals(input1, res2) ||
                !Arrays.equals(input1, res3) ||
                        false)
            throw new AssertionError("result does not match expectation");

        msg4 = bobSession.encrypt(input1);
        msg5 = bobSession.encrypt(input1);
        msg6 = bobSession.encrypt(input1);
        if (
                Arrays.equals(msg4, msg5) ||
                Arrays.equals(msg5, msg6) ||
                Arrays.equals(msg4, msg6) ||
                false)
            throw new AssertionError("sent identical messages");

        res4 = aliceSession.decrypt(msg4);
        res6 = aliceSession.decrypt(msg6);
        res5 = aliceSession.decrypt(msg5);
        if (
                !Arrays.equals(input1, res4) ||
                        !Arrays.equals(input1, res5) ||
                        !Arrays.equals(input1, res6) ||
                        false)
            throw new AssertionError("result does not match expectation");

        msg1 = aliceSession.encrypt(input1);
        msg4 = bobSession.encrypt(input1);
        msg2 = aliceSession.encrypt(input1);
        msg5 = bobSession.encrypt(input1);
        msg3 = aliceSession.encrypt(input1);
        msg6 = bobSession.encrypt(input1);

        res1 = bobSession.decrypt(msg1);
        res3 = bobSession.decrypt(msg3);
        res2 = bobSession.decrypt(msg2);
        if (
                !Arrays.equals(input1, res1) ||
                        !Arrays.equals(input1, res2) ||
                        !Arrays.equals(input1, res3) ||
                        false)
            throw new AssertionError("result does not match expectation");

        res4 = aliceSession.decrypt(msg4);
        res6 = aliceSession.decrypt(msg6);
        res5 = aliceSession.decrypt(msg5);
        if (
                !Arrays.equals(input1, res4) ||
                        !Arrays.equals(input1, res5) ||
                        !Arrays.equals(input1, res6) ||
                        false)
            throw new AssertionError("result does not match expectation");
    }


    void session_setup_test2() throws Exception {
        PrivateStore alicePrivateStore = generatePrivateStore();
        PrivateStore bobPrivateStore = generatePrivateStore();

        ECSignedPublicKey aliceSignedTemporaryKey = ECSignedPublicKey.sign(
                alicePrivateStore.getTemporaryKey(0).asPublicKey(), alicePrivateStore.getOurIdentity());
        ECSignedPublicKey bobSignedTemporaryKey = ECSignedPublicKey.sign(
                bobPrivateStore.getTemporaryKey(0).asPublicKey(), bobPrivateStore.getOurIdentity());

        if (!bobSignedTemporaryKey.verifySignature(bobPrivateStore.getOurIdentity().asPublicKey()))
            throw new AssertionError("bob's signed key does not verify");
        if (!aliceSignedTemporaryKey.verifySignature(alicePrivateStore.getOurIdentity().asPublicKey()))
            throw new AssertionError("alice's signed key does not verify");


        byte[] input1 = new byte[]{1, 2, 3};

        SessionStore aliceSessionStore = new SessionStore(new File(directory, "aliceSession"));
        SessionStore bobSessionStore = new SessionStore(new File(directory, "bobSession"));
        Session aliceSession = new Session(alicePrivateStore, aliceSessionStore);
        Session bobSession = new Session(bobPrivateStore, bobSessionStore);

        aliceSession.setup(bobPrivateStore.getOurIdentity().asPublicKey(), bobSignedTemporaryKey, 0,
                bobPrivateStore.getOneTimeKey(0).asPublicKey(), 0);
        bobSession.setup(bobPrivateStore.getOurIdentity().asPublicKey(), aliceSignedTemporaryKey, 0,
                alicePrivateStore.getOneTimeKey(0).asPublicKey(), 0);

        byte[] msg1 = aliceSession.encrypt(input1);
        byte[] msg2 = aliceSession.encrypt(input1);
        byte[] msg3 = aliceSession.encrypt(input1);

        if (Arrays.equals(msg1, msg2) || Arrays.equals(msg2, msg3) || Arrays.equals(msg1, msg3))
            throw new AssertionError("sent identical messages");

        byte[] msg4 = bobSession.encrypt(input1);
        byte[] msg5 = bobSession.encrypt(input1);
        byte[] msg6 = bobSession.encrypt(input1);
        if (
                Arrays.equals(msg4, msg5) ||
                        Arrays.equals(msg5, msg6) ||
                        Arrays.equals(msg4, msg6) ||
                        false)
            throw new AssertionError("sent identical messages");


        byte[] res1 = bobSession.decrypt(msg1);
        byte[] res3 = bobSession.decrypt(msg3);
        byte[] res2 = bobSession.decrypt(msg2);
        if (
                !Arrays.equals(input1, res1) ||
                        !Arrays.equals(input1, res2) ||
                        !Arrays.equals(input1, res3) ||
                        false)
            throw new AssertionError("result does not match expectation");


        byte[] res4 = aliceSession.decrypt(msg4);
        byte[] res6 = aliceSession.decrypt(msg6);
        byte[] res5 = aliceSession.decrypt(msg5);
        if (
                !Arrays.equals(input1, res4) ||
                        !Arrays.equals(input1, res5) ||
                        !Arrays.equals(input1, res6) ||
                        false)
            throw new AssertionError("result does not match expectation");

        msg1 = aliceSession.encrypt(input1);
        msg2 = aliceSession.encrypt(input1);
        msg3 = aliceSession.encrypt(input1);

        if (Arrays.equals(msg1, msg2) || Arrays.equals(msg2, msg3) || Arrays.equals(msg1, msg3))
            throw new AssertionError("sent identical messages");

        res1 = bobSession.decrypt(msg1);
        res3 = bobSession.decrypt(msg3);
        res2 = bobSession.decrypt(msg2);
        if (
                !Arrays.equals(input1, res1) ||
                        !Arrays.equals(input1, res2) ||
                        !Arrays.equals(input1, res3) ||
                        false)
            throw new AssertionError("result does not match expectation");

        msg4 = bobSession.encrypt(input1);
        msg5 = bobSession.encrypt(input1);
        msg6 = bobSession.encrypt(input1);
        if (
                Arrays.equals(msg4, msg5) ||
                        Arrays.equals(msg5, msg6) ||
                        Arrays.equals(msg4, msg6) ||
                        false)
            throw new AssertionError("sent identical messages");

        res4 = aliceSession.decrypt(msg4);
        res6 = aliceSession.decrypt(msg6);
        res5 = aliceSession.decrypt(msg5);
        if (
                !Arrays.equals(input1, res4) ||
                        !Arrays.equals(input1, res5) ||
                        !Arrays.equals(input1, res6) ||
                        false)
            throw new AssertionError("result does not match expectation");
    }
}
