package net.instantcom.keiko.bittorrent.protocol.encryption;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.bouncycastle.crypto.StreamCipher;

/**
 * Encrypted input stream.
 */
public class EncryptedInputStream extends FilterInputStream {

    /**
     * Creates new encrypted input stream. Stream cipher must be fully
     * initialized before calling any read method.
     * 
     * @param in
     *            input stream
     * @param streamCipher
     *            stream cipher
     */
    public EncryptedInputStream(InputStream in, StreamCipher streamCipher) {
        super(in);
        this.streamCipher = streamCipher;
        setEnabled(true);
    }

    /**
     * Gets stream cipher.
     * 
     * @return stream cipher
     */
    public StreamCipher getStreamCipher() {
        return streamCipher;
    }

    /**
     * Checks if decryption is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables decryption. When decryption is enabled data read from
     * stream is automatically decrypted. When decryption is disabled, behaviour
     * is exactly as regular input stream.
     * 
     * @param enabled
     *            true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            if (null == buffer) {
                buffer = new byte[1024];
            }
        } else {
            // free some heap
            buffer = null;
        }
    }

    /**
     * Sets initial payload for this stream. Initial payload is already
     * decrypted data fetched during handshake. It it exists it will be read
     * before fetching new data from the stream.
     * 
     * @param initialPayload
     *            the initialPayload to set
     */
    public void setInitialPayload(ByteArrayInputStream initialPayload) {
        this.initialPayload = initialPayload;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (null != initialPayload) {
            if (0 == initialPayload.available()) {
                // end of payload reached
                initialPayload = null;
            } else {
                int x =
                    initialPayload.read(b, off, Math.min(len, initialPayload
                        .available()));
                if (x > 0) {
                    off += x;
                    len -= x;
                }
                if (len <= 0) {
                    return x;
                }
            }
        }
        if (enabled) {
            int totalRead = 0;
            while (len > buffer.length) {
                int size = in.read(buffer, 0, buffer.length);
                if (size < 0) {
                    return size;
                }
                streamCipher.processBytes(buffer, 0, size, b, off);
                off += size;
                len -= size;
                totalRead += size;
            }
            if (len > 0) {
                int size = in.read(buffer, 0, len);
                if (size < 0) {
                    return size;
                }
                streamCipher.processBytes(buffer, 0, size, b, off);
                totalRead += size;
            }
            return totalRead > 0 ? totalRead : -1;
        } else {
            return in.read(b, off, len);
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        if (null != initialPayload) {
            if (0 == initialPayload.available()) {
                // end of payload reached
                initialPayload = null;
            } else {
                return initialPayload.read();
            }
        }
        if (enabled) {
            int b = in.read();
            if (b < 0) {
                return b;
            }
            b = streamCipher.returnByte((byte) b);
            if (b < 0) {
                b += 0x100;
            }
            return b;
        } else {
            return in.read();
        }
    }

    @Override
    public int available() throws IOException {
        return (null == initialPayload ? 0 : initialPayload.available())
            + super.available();
    }

    private StreamCipher streamCipher;
    private boolean enabled;
    private byte[] buffer;
    private ByteArrayInputStream initialPayload;

}
