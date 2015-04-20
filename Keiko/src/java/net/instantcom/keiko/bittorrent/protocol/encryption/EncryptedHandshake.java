package net.instantcom.keiko.bittorrent.protocol.encryption;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.HashMap;
import java.util.Random;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;

import net.instantcom.keiko.Server;
import net.instantcom.keiko.bittorrent.protocol.HandshakeException;
import net.instantcom.keiko.bittorrent.protocol.Torrent;
import net.instantcom.util.SHA1Util;

public class EncryptedHandshake {

    private static final Random random = new Random();
    private static final BigInteger P =
        new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A36210000000000090563",
            16);
    private static final BigInteger G = BigInteger.valueOf(2);
    private static final int CRYPTO_PLAINTEXT = 0x01;
    private static final int CRYPTO_RC4 = 0x02;
    // azureus supports CRYPTO_XOR and CRYPTO_AES but no specification is
    // available, utorrent supports only same things as we do
    private static final int MY_CRYPTO_SUPPORTED =
        CRYPTO_PLAINTEXT | CRYPTO_RC4;

    /**
     * Creates new encrypted handshake. Input and output streams will be
     * automatically buffered if they aren't already.
     * 
     * @param is
     *            input stream
     * @param os
     *            output stream
     */
    public EncryptedHandshake(InputStream is, OutputStream os) {
        {
            byte[] test = bigIntegerToByteArray(P, 96);
            if (96 != test.length) {
                throw new IllegalArgumentException(
                    "P is NOT 768 bits long! Length of P is "
                        + (8 * test.length));
            }
        }

        // create encrypted input and output streams with uninitialized ciphers
        eis =
            new EncryptedInputStream(is instanceof BufferedInputStream ? is
                : new BufferedInputStream(is), new RC4Engine());
        eos =
            new EncryptedOutputStream(os instanceof BufferedOutputStream ? os
                : new BufferedOutputStream(os), new RC4Engine());
        // en/decryption is disabled by default
        eis.setEnabled(false);
        eos.setEnabled(false);
        dis = new DataInputStream(eis);
        dos = new DataOutputStream(eos);
    }

    /**
     * Gets data input stream.
     * 
     * @return data input stream
     */
    public DataInputStream getDataInputStream() {
        return dis;
    }

    /**
     * Gets data output stream
     * 
     * @return data output stream
     */
    public DataOutputStream getDataOutputStream() {
        return dos;
    }

    /**
     * Attempts encrypted handshake.
     * 
     * @param infoHash
     *            info hash of torrent for outgoing connections, null for
     *            incoming connections
     * @return true if encrypted handshake was completed, false if incoming
     *         connection is plaintext (it's still possible to proceed with
     *         normal bt handshake)
     * @throws HandshakeException
     *             if encrypted handshake fails
     */
    public boolean doHandshake(byte[] infoHash) throws HandshakeException {
        try {
            final boolean incoming = null == infoHash;

            // create my public key
            DHPublicKey myPublicKey = startKeyExchange();

            // create my random pad
            byte[] myPad = new byte[1 + random.nextInt(512)];
            random.nextBytes(myPad);

            // create my zero pad
            byte[] myPadCD = new byte[1 + random.nextInt(512)];

            // VC is always zeroed
            byte[] myVC = new byte[8];

            // we don't have initial payload
            byte[] myIA = null; // zero length

            if (incoming) {
                // from now on i'm B
                boolean markSupported = dis.markSupported();
                if (markSupported) {
                    // check if it's unencrypted protocol
                    // test for \19Bit
                    dis.mark(5);
                    if (0x13426974 == dis.readInt()) {
                        dis.reset();
                        return false;
                    }
                    dis.reset();
                }
                // 1 A->B: Diffie Hellman Ya, PadA
                byte[] tmp = new byte[96]; // 768 bits
                dis.readFully(tmp);
                // finish key exchange
                finishKeyExchange(byteArrayToBigInteger(tmp));

                // 2 B->A: Diffie Hellman Yb, PadB
                dos.write(bigIntegerToByteArray(myPublicKey.getY(), 96));
                if (null != myPad) {
                    dos.write(myPad);
                }
                dos.flush();

                // 3 A->B: HASH('req1', S), HASH('req2', SKEY) xor HASH('req3',
                // S), ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)),
                // ENCRYPT(IA)
                {
                    boolean foundIt = false;
                    byte[] expected =
                        SHA1Util.getSHA1("req1".getBytes(), secretBytes);
                    int syncCount = 0;
                    int sameCount = 0;
                    while (syncCount < 628 /* && dis.available() > 0 */) {
                        int x = dis.readByte();
                        ++syncCount;
                        if (expected[sameCount] == x) {
                            ++sameCount;
                            if (sameCount >= expected.length) {
                                foundIt = true;
                                break;
                            }
                        } else {
                            sameCount = 0;
                        }
                    }
                    if (!foundIt) {
                        throw new HandshakeException(
                            "can't find HASH('req1', S)");
                    }
                }
                Torrent foundTorrent = null;
                {
                    byte[] req2Hash = new byte[20];
                    dis.readFully(req2Hash);
                    {
                        // unxor
                        byte[] xored =
                            SHA1Util.getSHA1("req3".getBytes(), secretBytes);
                        for (int i = 0; i < 20; i++) {
                            req2Hash[i] ^= xored[i];
                        }
                    }
                    // check if torrent is known
                    HashMap<String, Torrent> torrents = Server.getTorrents();
                    for (Torrent torrent : torrents.values()) {
                        byte[] expected =
                            SHA1Util.getSHA1("req2".getBytes(), torrent
                                .getMetaInfo().getInfoHash());
                        if (Arrays.equals(expected, req2Hash)) {
                            foundTorrent = torrent;
                            break;
                        }
                    }
                    if (null == foundTorrent) {
                        throw new HandshakeException("no such torrent");
                    }
                }
                // from now on everything is encrypted
                setupStreamCiphers(incoming, foundTorrent.getMetaInfo()
                    .getInfoHash());
                eis.setEnabled(true);
                eos.setEnabled(true);
                {
                    // let it be whatever it is, azureus doesn't check it either
                    // byte[] expectedVC = new byte[8];
                    byte[] hisVC = new byte[8];
                    dis.readFully(hisVC);
                    // if (!Arrays.equals(expectedVC, hisVC)) {
                    // throw new HandshakeException("wrong VC, got: "
                    // + Arrays.toString(hisVC));
                    // }
                }
                int hisCryptoProvide = dis.readInt();
                if (0 == (hisCryptoProvide & MY_CRYPTO_SUPPORTED)) {
                    throw new HandshakeException("unknown cryptoProvide: "
                        + hisCryptoProvide + " ("
                        + Integer.toBinaryString(hisCryptoProvide) + ")");
                }
                int size = dis.readUnsignedShort();
                if (size < 0 || size > 512) {
                    throw new IllegalArgumentException(
                        "padC has incorrect size: " + size);
                }
                while (size > 0) {
                    dis.readByte();
                    --size;
                }
                int hisIASize = dis.readUnsignedShort();
                // this is the tricky part, if it exists it is definitely
                // encrypted so we need to read and decrypt it now and save it
                // for later
                byte[] hisIA = null;
                if (hisIASize > 0) {
                    hisIA = new byte[hisIASize];
                    dis.readFully(hisIA);
                }

                // 4 B->A: ENCRYPT(VC, crypto_select, len(padD), padD),
                // ENCRYPT2(Payload Stream)
                dos.write(myVC);
                int myCryptoSelect = 0;
                if (0 != (hisCryptoProvide & CRYPTO_PLAINTEXT)) {
                    // prefer plain text from now on
                    myCryptoSelect = CRYPTO_PLAINTEXT;
                    fullyEncrypted = false;
                } else {
                    // rc4 continues
                    myCryptoSelect = CRYPTO_RC4;
                    fullyEncrypted = true;
                }
                dos.writeInt(myCryptoSelect);
                if (null == myPadCD) {
                    dos.writeShort(0);
                } else {
                    dos.writeShort(myPadCD.length);
                    dos.write(myPadCD);
                }
                if (CRYPTO_PLAINTEXT == myCryptoSelect) {
                    // protocol continues unencrypted
                    eis.setEnabled(false);
                    eos.setEnabled(false);
                } else {
                    // protocol continues encrypted
                    eis.setEnabled(true);
                    eos.setEnabled(true);
                }
                dos.flush();

                // this is to make sure eis will read preloaded and decrypted
                // data before rest of the stream
                eis.setInitialPayload(null == hisIA ? null
                    : new ByteArrayInputStream(hisIA));

                // 5 A->B: ENCRYPT2(Payload Stream)
                // payload stream is usual bt protocol including handshake
                // PeerConnection.doHandshake() should take care of the rest as
                // encryption and decryption are totally transparent
            } else {
                // from now on i'm A
                // 1 A->B: Diffie Hellman Ya, PadA
                dos.write(bigIntegerToByteArray(myPublicKey.getY(), 96));
                if (null != myPad) {
                    dos.write(myPad);
                }
                dos.flush();

                // 2 B->A: Diffie Hellman Yb, PadB
                byte[] tmp = new byte[96]; // 768 bits
                dis.readFully(tmp);
                // finish key exchange
                finishKeyExchange(byteArrayToBigInteger(tmp));

                // 3 A->B: HASH('req1', S), HASH('req2', SKEY) xor HASH('req3',
                // S), ENCRYPT(VC, crypto_provide, len(PadC), PadC, len(IA)),
                // ENCRYPT(IA)
                dos.write(SHA1Util.getSHA1("req1".getBytes(), secretBytes));

                byte[] hashReq2SKey =
                    SHA1Util.getSHA1("req2".getBytes(), infoHash);
                {
                    // xor
                    byte[] hashReq3S =
                        SHA1Util.getSHA1("req3".getBytes(), secretBytes);
                    for (int i = 0; i < hashReq2SKey.length; i++) {
                        hashReq2SKey[i] ^= hashReq3S[i];
                    }
                }
                dos.write(hashReq2SKey);

                // from now on outgoing traffic is encrypted
                setupStreamCiphers(incoming, infoHash);
                eos.setEnabled(true);

                dos.write(myVC);
                dos.writeInt(MY_CRYPTO_SUPPORTED);
                if (null == myPadCD) {
                    dos.writeShort(0);
                } else {
                    dos.writeShort(myPadCD.length);
                    dos.write(myPadCD);
                }
                if (null != myIA) {
                    dos.writeShort(myIA.length);
                    dos.write(myIA);
                } else {
                    dos.writeShort(0);
                }
                dos.flush();

                // 4 B->A: ENCRYPT(VC, crypto_select, len(padD), padD),
                // ENCRYPT2(Payload Stream)

                // synchronize on his VC (see below) - read undecrypted until
                // you hit encrypted 8x 0x00, turn on decryption in eis after
                // that
                byte[] expectedVC = new byte[8];
                {
                    StreamCipher syncCipher = new RC4Engine();
                    syncCipher.init(true,
                        keyParameterUsedToSetupOutputStreamCipher);
                    skip1024(syncCipher);
                    syncCipher.processBytes(new byte[8], 0, 8, expectedVC, 0);
                }
                boolean foundIt = false;
                {
                    int syncCount = 0;
                    int sameCount = 0;
                    while (syncCount < 616) {
                        int x = dis.readByte();
                        ++syncCount;
                        if (expectedVC[sameCount] == x) {
                            ++sameCount;
                            if (sameCount >= expectedVC.length) {
                                foundIt = true;
                                break;
                            }
                        } else {
                            sameCount = 0;
                        }
                    }
                }
                if (!foundIt) {
                    throw new HandshakeException("can't find VC");
                }

                // from now on incoming traffic is encrypted
                eis.setEnabled(true);
                // make sure input stream cipher is synced to remote output
                // cipher
                eis.getStreamCipher().processBytes(new byte[8], 0, 8,
                    new byte[8], 0);
                int hisCryptoSelect = dis.readInt();
                byte[] hisPadD = null;
                int size = dis.readShort();
                if (size > 0) {
                    hisPadD = new byte[size];
                    dis.readFully(hisPadD);
                }
                if (CRYPTO_PLAINTEXT == hisCryptoSelect) {
                    // protocol continues unencrypted
                    eis.setEnabled(false);
                    eos.setEnabled(false);
                    fullyEncrypted = false;
                } else if (CRYPTO_RC4 == hisCryptoSelect) {
                    // protocol continues encrypted
                    eis.setEnabled(true);
                    eos.setEnabled(true);
                    fullyEncrypted = true;
                } else {
                    throw new HandshakeException("unknown cryptoSelect: "
                        + hisCryptoSelect + " ("
                        + Integer.toBinaryString(hisCryptoSelect) + ")");
                }

                // 5 A->B: ENCRYPT2(Payload Stream)
                // payload stream is usual bt protocol including handshake
                // PeerConnection.doHandshake() should take care of the rest as
                // encryption and decryption are totally transparent
            }
        } catch (Exception e) {
            throw new HandshakeException(e);
        }
        return true;
    }

    // since BigInteger.toByteArray() returns all kind of wrong sized arrays we
    // need to do it manually
    private byte[] bigIntegerToByteArray(BigInteger big, int numBytes) {
        StringBuffer sb = new StringBuffer();
        String s = big.toString(16);
        int bSize = s.length();
        while (bSize < 2 * numBytes) {
            sb.append("0");
            ++bSize;
        }
        sb.append(s);
        s = sb.toString();
        if (2 * numBytes != s.length()) {
            throw new IllegalArgumentException(
                "string is of wrong size! should be " + (2 * numBytes)
                    + " but is " + s.length());
        }
        byte[] array = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            array[i] =
                (byte) Integer.parseInt(s.substring(2 * i, 2 + 2 * i), 16);
        }
        return array;
    }

    private BigInteger byteArrayToBigInteger(byte[] array) {
        return new BigInteger(SHA1Util.convertToString(array), 16);
    }

    private DHPublicKey startKeyExchange() throws NoSuchAlgorithmException,
        InvalidAlgorithmParameterException, InvalidKeyException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(new DHParameterSpec(P, G, 160));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        keyAgreement = KeyAgreement.getInstance("DH");
        keyAgreement.init(keyPair.getPrivate());
        return (DHPublicKey) keyPair.getPublic();
    }

    private void finishKeyExchange(BigInteger hisY)
        throws NoSuchAlgorithmException, InvalidKeySpecException,
        InvalidKeyException {
        KeyFactory keyFactory = KeyFactory.getInstance("DH");
        PublicKey hisPublicKey =
            keyFactory.generatePublic(new DHPublicKeySpec(hisY, P, G));
        keyAgreement.doPhase(hisPublicKey, true);
        secretBytes = keyAgreement.generateSecret();
    }

    private void setupStreamCiphers(boolean incoming, byte[] infoHash)
        throws NoSuchAlgorithmException {
        KeyParameter keyA =
            new KeyParameter(SHA1Util.getSHA1("keyA".getBytes(), secretBytes,
                infoHash));
        KeyParameter keyB =
            new KeyParameter(SHA1Util.getSHA1("keyB".getBytes(), secretBytes,
                infoHash));
        eis.getStreamCipher().init(false, incoming ? keyA : keyB);
        eos.getStreamCipher().init(true, incoming ? keyB : keyA);
        skip1024(eis.getStreamCipher());
        skip1024(eos.getStreamCipher());
        keyParameterUsedToSetupOutputStreamCipher = incoming ? keyA : keyB;
    }

    private void skip1024(StreamCipher streamCipher) {
        // skip first 1024 bytes of stream to be protected against a Fluhrer,
        // Mantin and Shamir attacks
        byte[] tmp = new byte[1024];
        streamCipher.processBytes(tmp, 0, tmp.length, tmp, 0);
    }

    public boolean isFullyEncrypted() {
        return fullyEncrypted;
    }

    private KeyAgreement keyAgreement;
    private byte[] secretBytes;
    private KeyParameter keyParameterUsedToSetupOutputStreamCipher;
    private final EncryptedInputStream eis;
    private final EncryptedOutputStream eos;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private boolean fullyEncrypted;

}
