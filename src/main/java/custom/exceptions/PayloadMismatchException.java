package custom.exceptions;

public class PayloadMismatchException extends RuntimeException {
    public PayloadMismatchException(String message) {
        super(message);
    }
}
