package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * Why a branch has no upstream to pull from, reset onto, or be compared with: none was set, or it was deleted on the
 * remote.
 */
public sealed interface UpstreamError extends AppError {

    /**
     * Why {@code branch} has no upstream.
     */
    static UpstreamError of(Branch branch) {
        return branch.goneUpstream()
                .<UpstreamError>map(upstream -> new Gone(branch.name(), upstream.name()))
                .getOrElse(() -> new None(branch.name()));
    }

    /**
     * No upstream was set for {@code branch}.
     */
    record None(BranchName branch) implements UpstreamError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.upstream.none", branch.value());
        }
    }

    /**
     * The upstream of {@code branch} was deleted on the remote.
     *
     * @param upstream the short name the upstream had, e.g. {@code origin/feature}
     */
    record Gone(BranchName branch, String upstream) implements UpstreamError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.upstream.gone", branch.value(), upstream);
        }
    }
}
