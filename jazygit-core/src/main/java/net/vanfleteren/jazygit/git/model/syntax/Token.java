package net.vanfleteren.jazygit.git.model.syntax;

/**
 * A piece of a line of code that is coloured by its syntax (ADR 53).
 *
 * @param start  where it starts in the line, in {@code char}s
 * @param length how many {@code char}s long it is
 * @param kind   what it is
 */
public record Token(int start, int length, Kind kind) {

    /**
     * The index just after it in its line.
     */
    public int end() {
        return start + length;
    }

    public enum Kind {
        KEYWORD,
        STRING,
        COMMENT,
        NUMBER
    }
}
