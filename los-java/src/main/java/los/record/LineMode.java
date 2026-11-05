package los.record;

/** How a LINE SEQUENTIAL dataset is written, matching GnuCOBOL's COB_LS_FIXED switch. */
public enum LineMode {
    /** COB_LS_FIXED=TRUE: every record keeps its full length, trailing filler included. */
    FIXED,
    /** GnuCOBOL default: trailing spaces are dropped before the line end (the HMDA CSV). */
    TRIMMED
}
