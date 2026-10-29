package net.vanfleteren.jazygit.git.model;

/**
 * Unified diff text as git printed it, cut off when it is too long to show.
 *
 * @param text      the diff, or only its first lines when it is truncated
 * @param truncated whether lines were left out
 * @param parsed    {@code text} parsed, with its words compared
 */
public record DiffText(String text, boolean truncated, ParsedDiff parsed) {

    /**
     * {@code text}, parsed where it is made, on the thread that read it from git (ADR 49).
     */
    public DiffText(String text, boolean truncated) {
        this(text, truncated, ParsedDiff.of(text));
    }

    /**
     * The whole of {@code text}.
     */
    public static DiffText of(String text) {
        return new DiffText(text, false);
    }

    public boolean isBlank() {
        return text.isBlank();
    }
}
