package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads whether the commit {@code sha} is on {@code upstream}, and on the main branch, on {@code origin} or locally,
 * in one {@code for-each-ref --contains}, before it is rewritten; then goes on with {@code then}. When that cannot be
 * read, the rewrite goes on without a warning: the warning only helps.
 *
 * @param upstream the remote branch the checked out branch follows
 * @param main     the main branch, if it is not the one checked out
 */
public record PushedCheckCmd(Sha sha, RemoteBranch upstream, Option<BranchName> main, Then then) implements Cmd {

    @Override
    public Msg run(Env env) {
        BranchRef onUpstream = new BranchRef.Remote(upstream);
        List<BranchRef> onMain = main.toList()
                .flatMap(name -> List.of(
                        new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name)),
                        new BranchRef.Local(name)));
        return env.git()
                .branches()
                .containing(sha, onMain.prepend(onUpstream).distinct())
                .fold(error -> failed(Failure.of(error)), having -> then.checked(warning(having, onUpstream, onMain)));
    }

    @Override
    public Msg failed(Failure failure) {
        return then.checked(Option.none());
    }

    /**
     * The warning, when {@code having}, the branches that have the commit, holds the upstream.
     */
    private Option<PushedWarning> warning(List<BranchRef> having, BranchRef onUpstream, List<BranchRef> onMain) {
        return Option.when(
                having.contains(onUpstream),
                () -> new PushedWarning(upstream, main.filter(name -> having.exists(onMain::contains))));
    }

    /**
     * What goes on with the rewrite once it is known whether the commit is pushed: a value of the feature, which makes
     * its message.
     */
    public interface Then {

        /**
         * The message that goes on with the rewrite, warning with {@code warning}, if any.
         */
        Msg checked(Option<PushedWarning> warning);
    }
}
