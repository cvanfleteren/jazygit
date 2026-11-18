package net.vanfleteren.jazygit.git.model;

import java.util.regex.Pattern;

/**
 * A regex as it was written, compiled. Two are equal when they were written alike: a {@link Pattern} is equal only to
 * itself, so a filter compiled again would differ from the one before, and what holds it would never be found equal
 * (ADR 144). It is made with {@link #of}.
 *
 * @param source  the regex as it was written
 * @param pattern {@code source} compiled
 */
public record Regex(String source, Pattern pattern) {

    /**
     * {@code source}, compiled; it throws when {@code source} is no regex, which the caller turns into a value.
     */
    public static Regex of(String source) {
        return new Regex(source, Pattern.compile(source));
    }

    /**
     * Whether it matches anywhere in {@code text}.
     */
    public boolean find(String text) {
        return pattern.matcher(text).find();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Regex(String same, var ignored) && same.equals(source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
