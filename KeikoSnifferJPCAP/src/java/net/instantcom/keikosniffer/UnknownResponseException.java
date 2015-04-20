package net.instantcom.keikosniffer;

public class UnknownResponseException extends Exception {

    private static final long serialVersionUID = 1L;

    public UnknownResponseException() {
    }

    public UnknownResponseException(String message) {
        super(message);
    }

    public UnknownResponseException(Throwable cause) {
        super(cause);
    }

    public UnknownResponseException(String message, Throwable cause) {
        super(message, cause);
    }

}
