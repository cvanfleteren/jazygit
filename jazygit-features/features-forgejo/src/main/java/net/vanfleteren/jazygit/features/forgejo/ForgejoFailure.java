package net.vanfleteren.jazygit.features.forgejo;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Something asked of Forgejo failed: a failure that says itself, as the kernel names no forge (ADR 171, 176).
 */
public sealed interface ForgejoFailure extends Failure.Phrased {

    /**
     * None: what asks Forgejo runs no command, and a browser that did not start ran none.
     */
    @Override
    default List<ExecutedCommand> commands() {
        return List.empty();
    }

    /**
     * Forgejo's API did not answer as asked.
     */
    record Asked(ForgejoError error) implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return switch (error) {
                case ForgejoError.Unreachable(String why) -> Phrase.of("forgejo.error.unreachable", why);
                case ForgejoError.Refused(int status, String why) ->
                    Phrase.of("forgejo.error.refused", String.valueOf(status));
                case ForgejoError.Unreadable(String why) -> Phrase.of("forgejo.error.unreadable", why);
            };
        }
    }

    /**
     * The home of the pull requests (ADR 177) is not, or no longer, on Forgejo.
     */
    record NoHome() implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.noHome");
        }
    }

    /**
     * The remote {@code remote}, whose checks were asked for, is not, or no longer, on Forgejo (ADR 195).
     */
    record NotOnForgejo(String remote) implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.notOnForgejo", remote);
        }
    }

    /**
     * The commit of the branch {@code branch}, whose checks were asked for, is not known (ADR 195).
     */
    record NoCommit(String branch) implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.noCommit", branch);
        }
    }

    /**
     * The log of a check could not be kept in {@code file}, as {@code why} says (ADR 195).
     */
    record LogNotKept(String file, String why) implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.logNotKept", file, why);
        }
    }

    /**
     * No browser could be found to open a link in.
     */
    record NoBrowser() implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.noBrowser");
        }
    }

    /**
     * The browser {@code browser} did not open the link.
     */
    record BrowserFailed(String browser) implements ForgejoFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.browserFailed", browser);
        }
    }
}
