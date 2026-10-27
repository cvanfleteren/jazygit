package net.vanfleteren.jazygit.feature.github.checkout;

import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.feature.github.GitHubRefusal;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg.Requested;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg.WorktreeRequested;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeUpdate;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Checking out a pull request listed on the pull requests screen.
 */
public final class CheckoutPullRequestUpdate {

    private CheckoutPullRequestUpdate() {}

    public static Next update(Model model, CheckoutPullRequestMsg msg) {
        return switch (msg) {
            case Requested(PullRequestSummary pullRequest) -> checkout(model, pullRequest);
            case WorktreeRequested(PullRequestSummary pullRequest) -> inWorktree(model, pullRequest);
        };
    }

    /**
     * Asks where the new worktree that checks out {@code pullRequest} goes, if it is of the repository; the changes
     * here stay here.
     */
    private static Next inWorktree(Model model, PullRequestSummary pullRequest) {
        WorktreeSource source = new WorktreeSource.PullRequest(pullRequest);
        return proceed(
                model,
                forWorktree(model, pullRequest, source),
                () -> AddWorktreeUpdate.ask(model.clearError(), source));
    }

    /**
     * Checks out {@code pullRequest} in place, if it is of the repository and the working tree has no changes to
     * bring along.
     */
    private static Next checkout(Model model, PullRequestSummary pullRequest) {
        return proceed(
                model,
                forCheckout(model, pullRequest),
                () -> Update.start(model.clearError(), new CheckoutPullRequestCmd(pullRequest)));
    }

    /**
     * Does what {@code readiness} calls for: tells why it is blocked, offers to switch to the worktree that has the
     * branch checked out, does nothing while it is busy, or else does {@code ready}.
     */
    private static Next proceed(Model model, Readiness readiness, Supplier<Next> ready) {
        return switch (readiness) {
            case Readiness.Blocked(AppError error) -> Next.of(model.fail(error));
            case Readiness.Elsewhere(BranchName branch, String worktree) ->
                Update.ask(model, new SwitchWorktreePopup(branch, worktree));
            case Readiness.Busy() -> Next.of(model);
            case Readiness.Ready() -> ready.get();
        };
    }

    /**
     * Whether a new worktree can check out {@code pullRequest}: not while it is unknown which repository this is, or
     * when it is of another one; not when this worktree has its branch checked out, nor while its worktree is being
     * added already. When another worktree has its branch checked out, switching to that is offered instead, as
     * checking it out in place does.
     */
    private static Readiness forWorktree(Model model, PullRequestSummary pullRequest, WorktreeSource source) {
        return ofThisRepository(model, pullRequest)
                .orElse(() -> checkedOutElsewhere(model, pullRequest))
                .orElse(() -> Option.when(
                        checkedOutHere(model, pullRequest),
                        () -> new Readiness.Blocked(new GitHubRefusal.PullRequestCheckedOutHere(name(pullRequest)))))
                .orElse(() -> Option.when(busy(model, source), Readiness.Busy::new))
                .getOrElse(Readiness.Ready::new);
    }

    /**
     * Whether {@code pullRequest} can be checked out in place: not while it is unknown which repository this is, or
     * when it is of another one; not when the working tree has changes to bring along. Untracked files do not count,
     * as git only stops for those that are in the way. When another worktree has its branch checked out, which git
     * would refuse, switching to that worktree is offered instead, as checking out the branch offers it.
     */
    private static Readiness forCheckout(Model model, PullRequestSummary pullRequest) {
        boolean changed = Update.files(model).exists(file -> file.type() != ChangeType.UNTRACKED);
        return ofThisRepository(model, pullRequest)
                .orElse(() -> checkedOutElsewhere(model, pullRequest))
                .orElse(() -> Option.when(
                        changed,
                        () -> new Readiness.Blocked(new GitHubRefusal.ChangesBeforeCheckout(name(pullRequest)))))
                .getOrElse(Readiness.Ready::new);
    }

    /**
     * Blocked while it is not known yet which repository this is, or when {@code pullRequest} is of another one.
     */
    private static Option<Readiness> ofThisRepository(Model model, PullRequestSummary pullRequest) {
        return model.prOverview()
                .ofThisRepository(pullRequest)
                .fold(
                        unknown -> Option.of(new Readiness.Blocked(unknown)),
                        ofThis -> Option.when(
                                !ofThis,
                                () -> new Readiness.Blocked(new GitHubRefusal.OtherRepository(name(pullRequest)))));
    }

    /**
     * The worktree that has the branch of {@code pullRequest} checked out, if another one has. The branch of one
     * from a fork gh names after its owner, so it is not looked for.
     */
    private static Option<Readiness> checkedOutElsewhere(Model model, PullRequestSummary pullRequest) {
        BranchName branch = new BranchName(pullRequest.headBranch());
        return Option.when(!pullRequest.fromFork(), branch)
                .flatMap(model.repo()::branch)
                .flatMap(Branch::worktreeLocation)
                .map(worktree -> new Readiness.Elsewhere(branch, worktree));
    }

    /**
     * Whether this worktree has the branch of {@code pullRequest} checked out.
     */
    private static boolean checkedOutHere(Model model, PullRequestSummary pullRequest) {
        return !pullRequest.fromFork()
                && model.repo()
                        .currentBranch()
                        .exists(current -> current.name().value().equals(pullRequest.headBranch()));
    }

    /**
     * Whether an operation runs on the branch of {@code source}, as when its worktree is being added already. The
     * branch need not be known here yet, so it is looked for among the busy ones rather than among the branches.
     */
    private static boolean busy(Model model, WorktreeSource source) {
        return source.busyBranch().exists(model.jobs().busy()::containsKey);
    }

    private static String name(PullRequestSummary pullRequest) {
        return pullRequest.repository() + "#" + pullRequest.number();
    }

    /**
     * What stands between a pull request and checking it out.
     */
    private sealed interface Readiness {

        /**
         * It cannot be checked out, for {@code error}.
         */
        record Blocked(AppError error) implements Readiness {}

        /**
         * Another worktree has its branch checked out, at {@code worktree}.
         */
        record Elsewhere(BranchName branch, String worktree) implements Readiness {}

        /**
         * Its worktree is being added already.
         */
        record Busy() implements Readiness {}

        record Ready() implements Readiness {}
    }
}
