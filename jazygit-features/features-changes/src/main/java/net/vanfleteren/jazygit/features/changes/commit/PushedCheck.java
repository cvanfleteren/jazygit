package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Whether to read if a commit is pushed before rewriting it, to warn that the next push has to be a force push
 * (#722): only on a checked out branch that follows a branch on a remote.
 */
public final class PushedCheck {

    /**
     * Whether rewriting a commit that is pushed already warns first. It stands in for a setting still to come; until
     * then the warning is always on.
     */
    public static final boolean WARN = true;

    private PushedCheck() {}

    /**
     * Reads whether {@code sha} is pushed and then goes on with {@code then}; when there is nothing to warn about,
     * goes on at once with {@code unwarned}, the same rewrite without a warning.
     */
    public static Next first(Model model, Sha sha, PushedCheckCmd.Then then, Supplier<Next> unwarned) {
        return of(WARN, model, sha, then)
                .map(read -> Next.of(model.clearError(), read))
                .getOrElse(unwarned);
    }

    /**
     * The command that reads whether {@code sha} is pushed and then goes on with {@code then}, when {@code warn}; none
     * when there is nothing to warn about.
     */
    static Option<PushedCheckCmd> of(boolean warn, Model model, Sha sha, PushedCheckCmd.Then then) {
        return model.repo()
                .currentBranch()
                .filter(current -> warn)
                .flatMap(current -> current.remoteUpstream()
                        .map(upstream -> new PushedCheckCmd(sha, upstream, otherMain(model), then)));
    }

    /**
     * The main branch, unless it is the one checked out.
     */
    static Option<BranchName> otherMain(Model model) {
        return model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(Branch::isDefault))
                .filter(main -> !main.current())
                .map(Branch::name);
    }
}
