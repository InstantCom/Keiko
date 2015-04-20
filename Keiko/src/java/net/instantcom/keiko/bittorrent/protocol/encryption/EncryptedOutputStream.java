package net.instantcom.keiko.bittorrent.protocol.encryption;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.bouncycastle.crypto.StreamCipher;

/**
 * Encrypted output stream.
 */
public class EncryptedOutputStream extends FilterOutputStream {

    /**
     * Creates new encrypted output stream. Specified stream cipher must be
     * fully initialized before calling any write method.
     * 
     * @param out
     *            output stream
     * @param streamCipher
     *            stream cipher
     */
    public EncryptedOutputStream(OutputStream out, StreamCipher streamCipher) {
        super(out);
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
     * Checks if encryption is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables encryption. When encryption is enabled data written
     * to stream is automatically encrypted. When encryption is disabled,
     * behaviour is exactly as regular output stream.
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

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (enabled) {
            while (len > buffer.length) {
                streamCipher.processBytes(b, off, buffer.length, buffer, 0);
                out.write(buffer, 0, buffer.length);
                off += buffer.length;
                len -= buffer.length;
            }
            if (len > 0) {
                streamCipher.processBytes(b, off, len, buffer, 0);
                out.write(buffer, 0, len);
            }
        } else {
            out.write(b, off, len);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(int b) throws IOException {
        if (enabled) {
            out.write(streamCipher.returnByte((byte) b));
        } else {
            out.write(b);
        }
    }

    private StreamCipher streamCipher;
    private boolean enabled;
    private byte[] buffer;

}
