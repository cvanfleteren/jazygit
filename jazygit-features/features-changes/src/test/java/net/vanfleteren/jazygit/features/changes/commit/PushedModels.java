package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;

/**
 * Models in which the checked out branch follows a branch on a remote, or not, for the tests of the warning before
 * rewriting a pushed commit.
 */
public final class PushedModels {

    public static final BranchName MAIN = new BranchName("main");
    public static final BranchName FEATURE = new BranchName("feature");
    public static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, FEATURE);

    private PushedModels() {}

    /**
     * {@code model} with {@code feature} checked out, following {@code origin/feature}, and {@code main}, the main
     * branch, next to it.
     */
    public static Model onFollowingBranch(Model model) {
        return withBranches(
                model,
                branch(
                        FEATURE,
                        true,
                        false,
                        new Tracking.Tracks(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 1, 0))),
                branch(MAIN, false, true, new Tracking.None()));
    }

    /**
     * {@code model} with the local branches {@code branches}.
     */
    public static Model withBranches(Model model, Branch... branches) {
        return TestModels.localBranchesRead(model, List.of(branches)).model();
    }

    public static Branch branch(BranchName name, boolean current, boolean isDefault, Tracking tracking) {
        return Fixtures.branch(name, current, new Sha("ffff"), Instant.EPOCH, true, isDefault, tracking);
    }

    /**
     * The warning that a commit is on {@code origin/feature}, and on {@code main} when {@code onMain}.
     */
    public static PushedWarning warning(boolean onMain) {
        return new PushedWarning(ORIGIN_FEATURE, Option.when(onMain, MAIN));
    }
}
