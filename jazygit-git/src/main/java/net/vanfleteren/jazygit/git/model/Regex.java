package net.vanfleteren.jazygit.git.model;

import java.util.regex.Pattern;

/**
 * A regex as it was written, compiled. Two are equal when they were written alike: a {@link Pattern} is equal only to
 * itself, so a filter compiled again would differ from the one before, and what holds it would never be found equal
 * (ADR 144). A class rather than a record, as its equality is not that of its parts: a record's is, and it is not to
 * be told otherwise. The architecture rules take it for a value by name ({@code Values.EXCEPTIONS} in
 * jazygit-test-support). It is made with {@link #of}.
 */
public final class Regex {

    private final String source;
    private final Pattern pattern;

    private Regex(String source, Pattern pattern) {
        this.source = source;
        this.pattern = pattern;
    }

    /**
     * {@code source}, compiled; it throws when {@code source} is no regex, which the caller turns into a value.
     */
    public static Regex of(String source) {
        return new Regex(source, Pattern.compile(source));
    }

    /**
     * The regex as it was written.
     */
    public String source() {
        return source;
    }

    /**
     * Whether it matches anywhere in {@code text}.
     */
    public boolean find(String text) {
        return pattern.matcher(text).find();
    }

    /**
     * Whether it matches all of {@code text}.
     */
    public boolean matches(String text) {
        return pattern.matcher(text).matches();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Regex regex && regex.source.equals(source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public String toString() {
        return "Regex[" + source + "]";
    }
}
