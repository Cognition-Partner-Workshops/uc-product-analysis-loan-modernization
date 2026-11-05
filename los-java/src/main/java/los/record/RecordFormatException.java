package los.record;

/** A record or field whose bytes do not fit its copybook layout. */
public class RecordFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RecordFormatException(String message) {
        super(message);
    }
}
