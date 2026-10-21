package net.vanfleteren.jazygit.feature.github.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg.Requested;
import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg.WorktreeRequested;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeUpdate;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Branch;
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
     * here stay here. When another worktree has its branch checked out, switching to it is offered instead, as
     * checking it out in place does; when this one has, that is said.
     */
    private static Next inWorktree(Model model, PullRequestSummary pullRequest) {
        String name = pullRequest.repository() + "#" + pullRequest.number();
        boolean checkedOutHere = !pullRequest.fromFork()
                && model.repo()
                        .currentBranch()
                        .exists(current -> current.name().value().equals(pullRequest.headBranch()));
        return model.prOverview()
                .ofThisRepository(pullRequest)
                .fold(
                        unknown -> Next.of(model.fail(unknown)),
                        ofThis -> !ofThis
                                ? Next.of(model.fail(new AppError.OtherRepository(name)))
                                : checkedOutElsewhere(model, pullRequest)
                                        .getOrElse(() -> checkedOutHere
                                                ? Next.of(model.fail(new AppError.PullRequestCheckedOutHere(name)))
                                                : AddWorktreeUpdate.ask(
                                                        model.clearError(),
                                                        new WorktreeSource.PullRequest(pullRequest))));
    }

    /**
     * Checks out {@code pullRequest} in place, if it is of the repository and the working tree has no changes to
     * bring along; untracked files do not count, as git only stops for those that are in the way. Until it is
     * known which repository this is, the user is told why it is not known yet. When another worktree has its
     * branch checked out, which git would refuse, switching to that worktree is offered instead, as checking out the
     * branch offers it.
     */
    private static Next checkout(Model model, PullRequestSummary pullRequest) {
        String name = pullRequest.repository() + "#" + pullRequest.number();
        boolean changed = Update.files(model).exists(file -> file.type() != ChangeType.UNTRACKED);
        return model.prOverview()
                .ofThisRepository(pullRequest)
                .fold(
                        unknown -> Next.of(model.fail(unknown)),
                        ofThis -> !ofThis
                                ? Next.of(model.fail(new AppError.OtherRepository(name)))
                                : checkedOutElsewhere(model, pullRequest)
                                        .getOrElse(() -> changed
                                                ? Next.of(model.fail(new AppError.ChangesBeforeCheckout(name)))
                                                : Update.start(
                                                        model.clearError(), new CheckoutPullRequestCmd(pullRequest))));
    }

    /**
     * Offers to switch to the worktree that has the branch of {@code pullRequest} checked out, if another one has.
     * The branch of one from a fork gh names after its owner, so it is not looked for.
     */
    private static Option<Next> checkedOutElsewhere(Model model, PullRequestSummary pullRequest) {
        Branch.Name branch = new Branch.Name(pullRequest.headBranch());
        return Option.when(!pullRequest.fromFork(), branch)
                .flatMap(model.repo()::branch)
                .flatMap(Branch::worktreeLocation)
                .map(worktree -> Update.ask(model, new SwitchWorktreePopup(branch, worktree)));
    }
}
