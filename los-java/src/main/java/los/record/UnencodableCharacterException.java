package los.record;

/** A value holds a character the ISO-8859-1 legacy datasets cannot store. */
public final class UnencodableCharacterException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public UnencodableCharacterException(String message) {
        super(message);
    }
}
