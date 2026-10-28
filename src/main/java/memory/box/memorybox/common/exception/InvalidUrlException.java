package memory.box.memorybox.common.exception;

/** Thrown when a user-supplied URL fails validation or is unsafe to process. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
