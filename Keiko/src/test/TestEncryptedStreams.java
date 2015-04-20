import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

import junit.framework.TestCase;

import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;

import net.instantcom.keiko.bittorrent.protocol.encryption.EncryptedInputStream;
import net.instantcom.keiko.bittorrent.protocol.encryption.EncryptedOutputStream;
import net.instantcom.util.SHA1Util;

public class TestEncryptedStreams extends TestCase {

    private static final BigInteger P =
        new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563",
            16);
    private static final BigInteger G = BigInteger.valueOf(2);
    private static final Random random = new Random();
    private static final String PLAIN_TEXT =
        "The following protocol describes a transparent wrapper for "
            + "bidirectional data streams (e.g. TCP transports) that prevents "
            + "passive eavesdroping and thus protocol or content "
            + "identification.\n"
            + "It is also designed to provide limited protection against "
            + "active MITM attacks and portscanning by requiring a weak shared "
            + "secret to complete the handshake. You should note that the "
            + "major design goal was payload and protocol obfuscation, not "
            + "peer authentication and data integrity verification. Thus it "
            + "does not offer protection against adversaries which already "
            + "know the necessary data to establish connections (that is "
            + "IP/Port/Shared Secret/Payload protocol).\n"
            + "To minimize the load on systems that employ this protocol fast "
            + "cryptographic methods have been chosen over maximum-security "
            + "algorithms.\n";

    public void test() throws Exception {
        assertTrue(PLAIN_TEXT.length() > 255);

        // test streams with both disabled and enabled encryption
        // 1x disabled, 99x enabled
        for (int i = 0; i < 100; i++) {
            // prepare random info hash
            final byte[] infoHash = new byte[20];
            random.nextBytes(infoHash);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // D-H key exchange
            KeyAgreement senderKeyAgreement = KeyAgreement.getInstance("DH");
            DHPublicKey senderPublicKey = startKeyExchange(senderKeyAgreement);
            KeyAgreement receiverKeyAgreement = KeyAgreement.getInstance("DH");
            DHPublicKey receiverPublicKey =
                startKeyExchange(receiverKeyAgreement);
            byte[] receiverSecretBytes =
                finishKeyExchange(receiverKeyAgreement, senderPublicKey.getY());
            byte[] senderSecretBytes =
                finishKeyExchange(senderKeyAgreement, receiverPublicKey.getY());
            assertTrue(Arrays.equals(senderSecretBytes, receiverSecretBytes));

            {
                // prepare encrypted output stream
                StreamCipher outCipher = new RC4Engine();
                setupStreamCipher(outCipher, senderSecretBytes, false, infoHash);
                EncryptedOutputStream eos =
                    new EncryptedOutputStream(baos, outCipher);
                eos.setEnabled(0 != i);
                DataOutputStream dos = new DataOutputStream(eos);

                // write text
                dos.write(PLAIN_TEXT.getBytes());
                dos.flush();

                // close
                eos.close();

                if (0 == i) {
                    assertTrue(Arrays.equals(PLAIN_TEXT.getBytes(), baos
                        .toByteArray()));
                } else {
                    assertFalse(Arrays.equals(PLAIN_TEXT.getBytes(), baos
                        .toByteArray()));
                }
            }

            {
                // prepare encrypted input stream
                StreamCipher inCipher = new RC4Engine();
                setupStreamCipher(inCipher, receiverSecretBytes, true, infoHash);
                EncryptedInputStream eis =
                    new EncryptedInputStream(new ByteArrayInputStream(baos
                        .toByteArray()), inCipher);
                eis.setEnabled(0 != i);
                DataInputStream dis = new DataInputStream(eis);

                // read text
                byte[] tmp = new byte[PLAIN_TEXT.getBytes().length];
                dis.readFully(tmp);

                // close
                eis.close();

                assertTrue(Arrays.equals(PLAIN_TEXT.getBytes(), tmp));
            }
        }
    }

    public void test2() throws Exception {
        EncryptedInputStream eis =
            new EncryptedInputStream(new ByteArrayInputStream("normal"
                .getBytes()), null);
        eis.setEnabled(false);
        eis.setInitialPayload(new ByteArrayInputStream("initial".getBytes()));
        assertEquals('i', eis.read());
        assertEquals('n', eis.read());
        assertEquals('i', eis.read());
        assertEquals('t', eis.read());
        assertEquals('i', eis.read());
        assertEquals('a', eis.read());
        assertEquals('l', eis.read());
        assertEquals('n', eis.read());
        assertEquals('o', eis.read());
        assertEquals('r', eis.read());
        assertEquals('m', eis.read());
        assertEquals('a', eis.read());
        assertEquals('l', eis.read());
        eis.close();
    }

    public void test3() throws Exception {
        EncryptedInputStream eis =
            new EncryptedInputStream(new ByteArrayInputStream("normal"
                .getBytes()), null);
        eis.setEnabled(false);
        eis.setInitialPayload(new ByteArrayInputStream("initial".getBytes()));
        byte[] tmp = new byte[13];
        eis.read(tmp);
        assertEquals("initialnormal", new String(tmp));
        eis.close();
    }

    public void test4() throws Exception {
        EncryptedInputStream eis =
            new EncryptedInputStream(new ByteArrayInputStream("normal"
                .getBytes()), null);
        eis.setEnabled(false);
        eis.setInitialPayload(new ByteArrayInputStream("initial".getBytes()));
        byte[] tmp = new byte[13];
        eis.read(tmp, 0, tmp.length);
        assertEquals("initialnormal", new String(tmp));
        eis.close();
    }

    private DHPublicKey startKeyExchange(KeyAgreement keyAgreement)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException,
        InvalidKeyException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(new DHParameterSpec(P, G, 160));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        keyAgreement.init(keyPair.getPrivate());
        return (DHPublicKey) keyPair.getPublic();
    }

    private byte[] finishKeyExchange(KeyAgreement keyAgreement, BigInteger hisY)
        throws NoSuchAlgorithmException, InvalidKeySpecException,
        InvalidKeyException {
        KeyFactory keyFactory = KeyFactory.getInstance("DH");
        PublicKey hisPublicKey =
            keyFactory.generatePublic(new DHPublicKeySpec(hisY, P, G));
        keyAgreement.doPhase(hisPublicKey, true);
        return keyAgreement.generateSecret();
    }

    private void setupStreamCipher(StreamCipher streamCipher,
        byte[] secretBytes, boolean incoming, byte[] infoHash)
        throws NoSuchAlgorithmException {
        KeyParameter key =
            new KeyParameter(SHA1Util.getSHA1("keyA".getBytes(), secretBytes,
                infoHash));
        streamCipher.init(!incoming, key);
        // skip first 1024 bytes of stream to be protected against a Fluhrer,
        // Mantin and Shamir attacks
        byte[] tmp = new byte[1024];
        streamCipher.processBytes(tmp, 0, tmp.length, tmp, 0);
    }

}
