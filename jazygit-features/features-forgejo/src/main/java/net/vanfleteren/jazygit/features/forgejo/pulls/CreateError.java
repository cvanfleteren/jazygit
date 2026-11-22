package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Why the pull request of a branch cannot be created on Forgejo, told before anything is pushed or asked (ADR 178).
 */
public sealed interface CreateError extends AppError {

    /**
     * {@code target}, where {@code branch} is pushed to, is not in a remote on the host of the home: Forgejo opens a
     * pull request only from a repository on its own host.
     */
    record Elsewhere(BranchName branch, RemoteBranch target) implements CreateError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "forgejo.error.create.elsewhere",
                    branch.value(),
                    target.remote().value(),
                    target.branch().value());
        }
    }

    /**
     * The home's remote has no branch that {@code branch} can merge into.
     */
    record NoBase(BranchName branch) implements CreateError {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.create.noBase", branch.value());
        }
    }

    /**
     * The home of the pull requests (ADR 177) is not, or no longer, on Forgejo.
     */
    record NoHome() implements CreateError {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.error.noHome");
        }
    }
}
