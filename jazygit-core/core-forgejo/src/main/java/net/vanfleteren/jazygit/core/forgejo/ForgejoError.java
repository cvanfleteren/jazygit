package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.control.Option;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.lib.json.Decoder;

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
    record Refused(int status, String why) implements ForgejoError {

        /**
         * A 409 to creating a pull request: one is open with the same head and base (ADR 178).
         */
        public static final int CONFLICT = 409;

        // Forgejo names the pull request that is open already in the text of its message, its number as issue_id.
        private static final Pattern EXISTING = Pattern.compile("pull request already exists.*issue_id: (\\d+)");

        private static final Decoder<String> MESSAGE = Decoder.field("message", Decoder.string());

        /**
         * The message of the answer, as Forgejo gives it, {@code {"message": "..."}}; the answer as it is when it
         * is not that.
         */
        public String message() {
            return MESSAGE.parse(why).getOrElse(why.strip());
        }

        /**
         * The number of the open pull request that has the same head and base, when this is the 409 of creating one
         * and the message names it (ADR 178).
         */
        public Option<Long> existing() {
            Matcher matcher = EXISTING.matcher(message());
            return Option.when(status == CONFLICT && matcher.find(), () -> Long.parseLong(matcher.group(1)));
        }
    }

    /**
     * The host answered with what is not what Forgejo answers.
     *
     * @param why what could not be read
     */
    record Unreadable(String why) implements ForgejoError {}
}
