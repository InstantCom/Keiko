package net.instantcom.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class SHA1Util {

    private static final String ALGORITHM = "SHA-1";

    public static byte[] getSHA1(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(ALGORITHM).digest(data);
    }

    public static byte[] getSHA1(byte[] data1, byte[] data2)
        throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        digest.update(data1);
        digest.update(data2);
        return digest.digest();
    }

    public static byte[] getSHA1(byte[] data1, byte[] data2, byte[] data3)
        throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        digest.update(data1);
        digest.update(data2);
        digest.update(data3);
        return digest.digest();
    }

    public static String convertToString(byte[] data) {
        if (null == data) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int b : data) {
            if (b < 0) {
                b += 0x100;
            }
            if (b >= 0 && b < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

}
