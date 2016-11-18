package wtf.kl.locshare.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteUtil {
    public static byte[] combine(byte[]... elements) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            for (byte[] element : elements) {
                baos.write(element);
            }

            return baos.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static byte[] cut(byte[] payload, int offset, int length) {
        byte[] p = new byte[length];
        System.arraycopy(payload, offset, p, 0, length);
        return p;
    }

    public static byte[] cut(ByteBuffer payload, int length) {
        byte[] p = new byte[length];
        payload.get(p);
        return p;
    }

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
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
