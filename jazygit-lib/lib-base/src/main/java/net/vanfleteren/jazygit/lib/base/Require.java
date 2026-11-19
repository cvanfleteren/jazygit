package net.vanfleteren.jazygit.lib.base;

import java.util.function.Supplier;

/**
 * Guards against a mistake in the code: a call that breaks what a method needs of its arguments. Such a mistake
 * is found by a test, not shown to the user, so it fails by throwing. What can go wrong while jazygit runs, as
 * reading a broken file, is a value instead: a {@code Try} or an {@code Either}.
 *
 * <p>Not Java's {@code assert}: the JVM skips it unless started with {@code -ea}, so the guard would be gone in the
 * jar and the native image.
 */
public final class Require {

    private Require() {}

    /**
     * Throws an {@link IllegalArgumentException} with what {@code problem} says when {@code condition} does not hold.
     *
     * @param condition what has to hold
     * @param problem   what is wrong when it does not; only made then
     */
    public static void that(boolean condition, Supplier<String> problem) {
        if (!condition) {
            throw new IllegalArgumentException(problem.get());
        }
    }
}
