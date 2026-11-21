package net.vanfleteren.jazygit.lib.syntax;

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
        NUMBER,
        /**
         * An annotation, or a decorator: {@code @Override}, {@code @lombok.Getter}.
         */
        ANNOTATION,
        /**
         * A name a value is given to, with the points of its path: Nix's {@code services.nginx.enable = true;}
         * (ADR 170).
         */
        PROPERTY
    }
}
