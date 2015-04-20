package net.instantcom.util.bcoder;

public class BDecodingException extends Exception {

    private static final long serialVersionUID = 1L;

    public BDecodingException() {
    }

    public BDecodingException(String message) {
        super(message);
    }

    public BDecodingException(Throwable cause) {
        super(cause);
    }

    public BDecodingException(String message, Throwable cause) {
        super(message, cause);
    }

}
