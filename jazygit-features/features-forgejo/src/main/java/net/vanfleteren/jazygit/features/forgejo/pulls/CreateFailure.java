package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Why creating a pull request on Forgejo failed, saying what to do about it (ADR 178).
 */
public sealed interface CreateFailure extends Failure.Phrased {

    /**
     * None: creating it runs no command.
     */
    @Override
    default List<ExecutedCommand> commands() {
        return List.empty();
    }

    /**
     * {@code branch} has no commit that {@code base} lacks, so the pull request would be empty.
     */
    record Empty(BranchName branch, BranchName base) implements CreateFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.create.empty", branch.value(), base.value());
        }
    }

    /**
     * {@code branch} has pull request {@code number} already, which the pull requests read before did not have.
     */
    record AlreadyOpen(BranchName branch, long number) implements CreateFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.create.exists", branch.value(), String.valueOf(number));
        }
    }

    /**
     * The branch is not, or no longer, pushed to a remote on the host of the home.
     */
    record Elsewhere(BranchName branch) implements CreateFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.create.gone", branch.value());
        }
    }

    /**
     * Forgejo did not create it, as {@code error} says.
     *
     * @param host       the host of the home, which fj keeps the token under
     * @param repository the home's repository, as {@code owner/name}
     * @param fork       the repository the branch is in, when that is not the home's
     */
    record Refused(ForgejoError error, String host, String repository, Option<String> fork) implements CreateFailure {

        private static final int UNAUTHORIZED = 401;
        private static final int FORBIDDEN = 403;
        private static final int NOT_FOUND = 404;

        @Override
        public Phrase phrase() {
            return switch (error) {
                case ForgejoError.Refused refused
                when refused.status() == UNAUTHORIZED -> Phrase.of("forgejo.error.create.token", host);
                case ForgejoError.Refused refused
                when refused.status() == FORBIDDEN ->
                    Phrase.of("forgejo.error.create.forbidden", repository, refused.message());
                case ForgejoError.Refused refused
                when refused.status() == NOT_FOUND && fork.isDefined() ->
                    Phrase.of("forgejo.error.create.notFork", fork.get(), repository);
                case ForgejoError.Refused refused ->
                    Phrase.of("forgejo.error.create.refused", String.valueOf(refused.status()), refused.message());
                case ForgejoError.Unreachable(String why) -> Phrase.of("forgejo.error.unreachable", why);
                case ForgejoError.Unreadable(String why) -> Phrase.of("forgejo.error.unreadable", why);
            };
        }
    }
}
