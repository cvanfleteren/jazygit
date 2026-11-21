package net.vanfleteren.jazygit.core.forgejo;

/**
 * Why asking Forgejo failed, as a value (ADR 174).
 */
public sealed interface ForgejoError {

    /**
     * What it says of itself, for the log.
     */
    String why();

    /**
     * The host could not be reached, or did not answer in time.
     *
     * @param why what went wrong
     */
    record Unreachable(String why) implements ForgejoError {}

    /**
     * The host answered with {@code status}, not 200: a repository it does not have, or one the token may not read,
     * is 404.
     *
     * @param status the HTTP status
     * @param why    the message it gave
     */
    record Refused(int status, String why) implements ForgejoError {}

    /**
     * The host answered with what is not what Forgejo answers.
     *
     * @param why what could not be read
     */
    record Unreadable(String why) implements ForgejoError {}
}
