package custom.exceptions;

public class DuplicateRequestException extends RuntimeException{
    public DuplicateRequestException(String message) {
        super(message);
    }
}
