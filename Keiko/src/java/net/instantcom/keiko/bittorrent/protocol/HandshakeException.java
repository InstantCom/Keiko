package net.instantcom.keiko.bittorrent.protocol;

public class HandshakeException extends Exception {

    private static final long serialVersionUID = 20080125L;

    public HandshakeException() {
    }

    public HandshakeException(String message) {
        super(message);
    }

    public HandshakeException(Throwable cause) {
        super(cause);
    }

    public HandshakeException(String message, Throwable cause) {
        super(message, cause);
    }

}
