package net.vanfleteren.jazygit.features.github.create;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.branches.branch.base.SetBaseCmd;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestUpdate;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestCmd.Base;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestCmd.ReadParent;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestCmd.RecordParent;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestMsg.*;
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.stack.StackPopups;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Creating the pull request of a branch on GitHub: pushing it first when it is not on its remote, reading the branch
 * it came from and the title it starts out with, then asking what it merges into and what it says, in one popup.
 * Pushing it first and the title that follows the base are {@link CreatePullRequestUpdate}'s.
 */
public final class GitHubCreatePullRequestUpdate {

    private GitHubCreatePullRequestUpdate() {}

    public static Next update(Model model, GitHubCreatePullRequestMsg msg) {
        return switch (msg) {
            case ParentRead(var branch, var parent) -> parentRead(model, branch, parent);
            case PullRequestEdited(NewPullRequest pullRequest) -> pullRequestEdited(model, pullRequest);
            case StackToggled() -> stackToggled(model);
            case StackRequested() -> stackRequested(model);
            case ParentRecorded(var cmd, var commands) -> parentRecorded(model, cmd, commands);
            case Confirmed() -> confirmed(model);
            case Created(CreatePullRequest cmd, var pullRequest, var commands) ->
                created(model, cmd, pullRequest, commands);
        };
    }

    /**
     * Asks about the pull request of {@code branch} now that its parent was read. The branch may have gone while its
     * parent was read; then there is nothing to ask.
     */
    private static Next parentRead(Model model, BranchName branch, Option<BranchName> parent) {
        return model.repo()
                .branch(branch)
                .map(known -> readTitle(model, known, parentFirst(bases(model, branch), parent)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The popup with what the new pull request says as the user changed it.
     */
    private static Next pullRequestEdited(Model model, NewPullRequest pullRequest) {
        return Next.of(model.popup(GitHubNewPullRequestPopup.class)
                .map(popup -> model.openPopup(popup.withPullRequest(pullRequest)))
                .getOrElse(model));
    }

    /**
     * The popup with stacking on the pull request of its base ticked or unticked.
     */
    private static Next stackToggled(Model model) {
        return Next.of(model.popup(GitHubNewPullRequestPopup.class)
                .map(popup -> model.openPopup(popup.withStack(!popup.stack())))
                .getOrElse(model));
    }

    /**
     * Creates the pull request the popup describes. A blank title keeps the popup open, as GitHub needs one.
     */
    private static Next confirmed(Model model) {
        if (model.popup(GitHubNewPullRequestPopup.class)
                .exists(popup -> popup.pullRequest().title().isBlank())) {
            return Next.of(model);
        }
        return PopupUpdate.confirm(
                model,
                GitHubNewPullRequestPopup.class,
                popup -> model.repo()
                        .branch(popup.branch())
                        .map(known -> new CreatePullRequest(
                                popup.branch(),
                                popup.head(),
                                Base.of(model, popup.base(), baseRef(model, popup.base()))
                                        .remote(),
                                stripped(popup.pullRequest()),
                                Option.when(popup.stack(), () -> stackOn(model, popup.base()))
                                        .flatMap(on -> on)
                                        .flatMap(Either::toOption))));
    }

    /**
     * Records the base asked about as the parent of the branch, to put its pull request on a stack once created
     * (ADR 148). A branch that is busy, or gone, is not.
     */
    private static Next stackRequested(Model model) {
        return model.popup(StackPopups.CreateFirst.class)
                .map(popup -> {
                    Model closed = model.withoutPopup(StackPopups.CreateFirst.class);
                    return model.idleBranch(popup.branch())
                            .map(known -> OperationUpdate.start(
                                    closed.clearError(),
                                    new RecordParent(new SetBaseCmd(popup.branch(), Option.of(popup.base())))))
                            .getOrElse(() -> Next.of(closed));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once the parent is recorded, asks what the pull request says, as asking for the pull request of a branch without
     * one does: pushing it first where that is needed. The base is the parent, offered first, and stacking on its pull
     * request is ticked.
     */
    private static Next parentRecorded(Model model, RecordParent cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> done.repo()
                        .branch(cmd.branch())
                        .map(known -> askPushed(done, known))
                        .getOrElse(() -> Next.of(done)));
    }

    /**
     * Done like any operation. The new pull request shows at once, as GitHub may not list it yet; reading the pull
     * requests then gives its checks and review.
     */
    private static Next created(
            Model model, CreatePullRequest cmd, Option<PullRequest> pullRequest, List<ExecutedCommand> commands) {
        Next done = OperationUpdate.succeeded(model, cmd, commands)
                .map(succeeded -> pullRequest
                        .map(pr -> succeeded.mapPart(
                                GitHubState.PART,
                                gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                                        .map(open -> open.put(FromGitHub.branch(pr.headBranch()), pr)))))
                        .getOrElse(succeeded));
        // A read that is still running may have missed it, so this one is started anyway. GitHub may have
        // changed the remote too, which a fetch brings in.
        Next read = Events.happened(PullRequestReads.loadAnyway(done), new KernelEvent.RemoteChanged());
        // Stacked once it is there: on the pull request of its base, as the dialog said (ADR 73).
        return cmd.stacking()
                .flatMap(stacking -> pullRequest.map(pr -> stacking.cmd(pr.number())))
                .map(stack -> read.and(stack))
                .getOrElse(read);
    }

    /**
     * Where a new pull request on {@code base} goes in a stack (ADR 73): none when {@code base} has no open pull
     * request; why it cannot when that one is in a stack but not on its top.
     */
    static Option<Either<GitHubCreatePullRequestCmd.NotTop, GitHubCreatePullRequestCmd.Stacking>> stackOn(
            Model model, BranchName base) {
        return model.repo()
                .branch(base)
                .flatMap(model.part(GitHubState.PART)::pullRequest)
                .map(below -> below.stack()
                        .map(stack -> stack.isTop()
                                ? Either.<GitHubCreatePullRequestCmd.NotTop, GitHubCreatePullRequestCmd.Stacking>right(
                                        new GitHubCreatePullRequestCmd.Stacking(
                                                below.number(), Option.of(stack.number())))
                                : Either.<GitHubCreatePullRequestCmd.NotTop, GitHubCreatePullRequestCmd.Stacking>left(
                                        new GitHubCreatePullRequestCmd.NotTop(below.number(), stack.number())))
                        .getOrElse(() ->
                                Either.right(new GitHubCreatePullRequestCmd.Stacking(below.number(), Option.none()))));
    }

    /**
     * {@code pullRequest} without the whitespace around its title.
     */
    private static NewPullRequest stripped(NewPullRequest pullRequest) {
        return new NewPullRequest(pullRequest.title().strip(), pullRequest.body(), pullRequest.draft());
    }

    /**
     * Asks what the pull request of {@code branch}, which has none yet, merges into and what it says, as
     * {@link #ask} does, once it is on its remote with all its commits: before that, the user is asked to push it
     * there (ADR 25).
     */
    public static Next askPushed(Model model, Branch branch) {
        return CreatePullRequestUpdate.askPushed(model, branch, new AskPullRequest());
    }

    /**
     * Asks what the pull request of {@code branch}, which has none yet, merges into and what it says, once the title
     * it starts out with is read, and before that, with several to choose from, the branch it came from, which is
     * offered first. With none to merge into, there is nothing to ask.
     */
    public static Next ask(Model model, Branch branch) {
        List<BranchName> bases = bases(model, branch.name());
        return bases.size() < 2
                ? readTitle(model, branch, bases)
                : Next.of(
                        model,
                        new ReadParent(branch.name(), bases.map(base -> Base.of(model, base, baseRef(model, base)))));
    }

    /**
     * Reads the title the pull request of {@code branch} into the first of {@code bases} starts out with. The branch
     * can have another name on its remote; gh needs that one. Without bases, which the branches read while the
     * parent was can also leave, there is nothing to ask.
     */
    private static Next readTitle(Model model, Branch branch, List<BranchName> bases) {
        return bases.headOption()
                .map(first -> Next.of(
                        model,
                        new ReadTitle(
                                branch.name(),
                                baseRef(model, first),
                                new GitHubNewPullRequestPopup.Opening(branch.name(), branch.pushTarget(), bases))))
                .getOrElse(() -> Next.of(model.fail(new GitHubError.NoBase(branch.name()))));
    }

    /**
     * {@code bases} with {@code parent} first, if it is one of them.
     */
    static List<BranchName> parentFirst(List<BranchName> bases, Option<BranchName> parent) {
        return parent.filter(bases::contains)
                .map(first -> bases.remove(first).prepend(first))
                .getOrElse(bases);
    }

    /**
     * The branch {@code base} is on its remote, which a pull request merges into; the local one when it has no
     * upstream there.
     */
    static BranchRef baseRef(Model model, BranchName base) {
        return model.repo()
                .branch(base)
                .flatMap(Branch::remoteUpstream)
                .<BranchRef>map(BranchRef.Remote::new)
                .getOrElse(() -> new BranchRef.Local(base));
    }

    /**
     * The branches a pull request from {@code branch} can merge into: the others that are on their remote, the
     * default branch first.
     */
    static List<BranchName> bases(Model model, BranchName branch) {
        return model.repo()
                .branches()
                .toOption()
                .getOrElse(List.empty())
                .filter(b -> b.hasRemote() && !b.name().equals(branch))
                .sortBy(b -> !b.isDefault())
                .map(Branch::name);
    }
}
