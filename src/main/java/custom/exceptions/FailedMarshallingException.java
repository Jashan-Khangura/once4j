package custom.exceptions;

public class FailedMarshallingException extends RuntimeException{
    public FailedMarshallingException(String message, Exception error) {
        super(message, error);
    }
}
