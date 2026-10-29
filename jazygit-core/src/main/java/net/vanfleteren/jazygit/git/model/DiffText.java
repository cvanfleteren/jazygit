package net.vanfleteren.jazygit.git.model;

/**
 * Unified diff text as git printed it, cut off when it is too long to show. It is made with {@link #of}, which parses
 * the text where it is made, on the thread that read it from git (ADR 50). A record's constructor is as public as the
 * record, so it cannot be hidden: it is not to be called with a {@code parsed} that is not {@code text} parsed.
 *
 * <p>Two are equal when their text is, and both are truncated or neither: {@code parsed} follows from {@code text},
 * and comparing it too would walk the whole parsed diff each time a reloaded diff is compared with the one shown.
 *
 * @param text      the diff, or only its first lines when it is truncated
 * @param truncated whether lines were left out
 * @param parsed    {@code text} parsed, with its words compared
 */
public record DiffText(String text, boolean truncated, ParsedDiff parsed) {

    /**
     * {@code text}, parsed.
     */
    public static DiffText of(String text, boolean truncated) {
        return new DiffText(text, truncated, ParsedDiff.of(text));
    }

    /**
     * The whole of {@code text}.
     */
    public static DiffText of(String text) {
        return of(text, false);
    }

    public boolean isBlank() {
        return text.isBlank();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DiffText(String otherText, boolean otherTruncated, ParsedDiff ignored)
                && truncated == otherTruncated
                && text.equals(otherText);
    }

    @Override
    public int hashCode() {
        return 31 * text.hashCode() + Boolean.hashCode(truncated);
    }
}
