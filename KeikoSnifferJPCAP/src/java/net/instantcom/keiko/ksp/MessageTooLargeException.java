package net.instantcom.keiko.ksp;

public class MessageTooLargeException extends Exception {

    private static final long serialVersionUID = 1L;

    public MessageTooLargeException() {
    }

    public MessageTooLargeException(String message) {
        super(message);
    }

    public MessageTooLargeException(Throwable cause) {
        super(cause);
    }

    public MessageTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }

}
