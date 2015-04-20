package net.instantcom.keikosniffer.stream;

public class StreamTooLargeException extends Exception {

    private static final long serialVersionUID = 2925970924475415892L;

    public StreamTooLargeException() {
    }

    public StreamTooLargeException(String message) {
        super(message);
    }

    public StreamTooLargeException(Throwable cause) {
        super(cause);
    }

    public StreamTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }

}
