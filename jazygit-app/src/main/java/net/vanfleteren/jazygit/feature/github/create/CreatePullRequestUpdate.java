package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchError;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.Base;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadParent;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.operation.*;
import net.vanfleteren.jazygit.state.popup.ListCursor;

/**
 * Creating the pull request of a branch: pushing it first when it is not on its remote, reading the branch it came
 * from and the title it starts out with, then
 * asking what it merges into and what it says, in one popup, where the title follows the base until the user types
 * one.
 */
public final class CreatePullRequestUpdate {

    private CreatePullRequestUpdate() {}

    public static Next update(Model model, CreatePullRequestMsg msg) {
        return switch (msg) {
            case ParentRead(var branch, var parent) -> parentRead(model, branch, parent);
            case TitleRead(var branch, var head, var bases, var subject) ->
                titleRead(model, branch, head, bases, subject);
            case BaseMoved(var cursor, var base) -> baseMoved(model, cursor, base);
            case BaseTitleRead(var branch, var base, var subject) -> baseTitleRead(model, branch, base, subject);
            case PullRequestEdited(NewPullRequest pullRequest) -> pullRequestEdited(model, pullRequest);
            case StackToggled() -> stackToggled(model);
            case Confirmed() -> confirmed(model);
            case PushFirstConfirmed() -> pushFirstConfirmed(model);
            case Pushed(PushFirst cmd, var commands) -> pushed(model, cmd, commands);
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
     * Opens the popup with the title read. Without a commit of its own to name it after, it is named after the
     * branch, as gh would.
     */
    private static Next titleRead(
            Model model, BranchName branch, RemoteBranch head, List<BranchName> bases, Option<String> subject) {
        String title = subject.getOrElse(branch::value);
        return Next.of(model.clearError()
                .openPopupIfFree(new NewPullRequestPopup(
                        branch, head, bases, bases.head(), title, new NewPullRequest(title, "", false), true)));
    }

    /**
     * Moves in the list of bases to {@code cursor}, as {@link #moved} does.
     */
    private static Next baseMoved(Model model, ListCursor cursor, Option<BranchName> base) {
        return model.popup(NewPullRequestPopup.class)
                .map(popup -> moved(model.withListCursor(cursor), popup, base))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Takes the title read for {@code base}. A title read for a base no longer picked, or after the user typed one,
     * is dropped.
     */
    private static Next baseTitleRead(Model model, BranchName branch, BranchName base, Option<String> subject) {
        return Next.of(model.popup(NewPullRequestPopup.class)
                .filter(popup -> popup.branch().equals(branch) && popup.base().equals(base) && popup.titleUntouched())
                .map(popup -> model.openPopup(titled(popup, subject.getOrElse(branch::value))))
                .getOrElse(model));
    }

    /**
     * The popup with what the new pull request says as the user changed it.
     */
    private static Next pullRequestEdited(Model model, NewPullRequest pullRequest) {
        return Next.of(model.popup(NewPullRequestPopup.class)
                .map(popup -> model.openPopup(popup.withPullRequest(pullRequest)))
                .getOrElse(model));
    }

    /**
     * The popup with stacking on the pull request of its base ticked or unticked.
     */
    private static Next stackToggled(Model model) {
        return Next.of(model.popup(NewPullRequestPopup.class)
                .map(popup -> model.openPopup(popup.withStack(!popup.stack())))
                .getOrElse(model));
    }

    /**
     * Creates the pull request the popup describes. A blank title keeps the popup open, as GitHub needs one.
     */
    private static Next confirmed(Model model) {
        if (model.popup(NewPullRequestPopup.class)
                .exists(popup -> popup.pullRequest().title().isBlank())) {
            return Next.of(model);
        }
        return Update.confirm(
                model,
                NewPullRequestPopup.class,
                popup -> model.repo()
                        .branch(popup.branch())
                        .map(known -> new CreatePullRequest(
                                popup.branch(),
                                popup.head(),
                                new Base(popup.base(), baseRef(model, popup.base())).remote(),
                                stripped(popup.pullRequest()),
                                Option.when(popup.stack(), () -> stackOn(model, popup.base()))
                                        .flatMap(on -> on)
                                        .flatMap(Either::toOption))));
    }

    /**
     * Pushes the branch before its pull request is created. The branches may have been read again while the user
     * was asked: with nothing to push any more, it was pushed meanwhile or has diverged since, so it is asked about
     * again as it is now.
     */
    private static Next pushFirstConfirmed(Model model) {
        return model.popup(PushFirstPopup.class)
                .flatMap(popup -> model.idleBranch(popup.branch()))
                .filter(known -> pushFirst(known).isEmpty())
                .map(known -> askPushed(model.withoutPopup(PushFirstPopup.class), known))
                .getOrElse(() -> Update.confirm(
                        model,
                        PushFirstPopup.class,
                        popup -> model.idleBranch(popup.branch()).flatMap(CreatePullRequestUpdate::pushFirst)));
    }

    /**
     * Done like any push. Asking needs the other branches and the name the branch has on its remote, which is
     * where it was just pushed to, so it does not wait for the branches to be read again.
     */
    private static Next pushed(Model model, PushFirst cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> done.repo()
                        .branch(cmd.branch())
                        .map(known -> ask(done, known))
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
                        .map(pr -> succeeded.mapGitHub(gitHub -> gitHub.withPullRequests(
                                gitHub.pullRequests().map(open -> open.put(FromGitHub.branch(pr.headBranch()), pr)))))
                        .getOrElse(succeeded));
        // A read that is still running may have missed it, so this one is started anyway. GitHub may have
        // changed the remote too, which a fetch brings in.
        Next read = FetchUpdate.requested(PullRequestsUpdate.loadAnyway(done));
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
    static Option<Either<CreatePullRequestCmd.NotTop, CreatePullRequestCmd.Stacking>> stackOn(
            Model model, BranchName base) {
        return model.repo()
                .branch(base)
                .flatMap(model.gitHub()::pullRequest)
                .map(below -> below.stack()
                        .map(stack -> stack.isTop()
                                ? Either.<CreatePullRequestCmd.NotTop, CreatePullRequestCmd.Stacking>right(
                                        new CreatePullRequestCmd.Stacking(below.number(), Option.of(stack.number())))
                                : Either.<CreatePullRequestCmd.NotTop, CreatePullRequestCmd.Stacking>left(
                                        new CreatePullRequestCmd.NotTop(below.number(), stack.number())))
                        .getOrElse(
                                () -> Either.right(new CreatePullRequestCmd.Stacking(below.number(), Option.none()))));
    }

    /**
     * {@code pullRequest} without the whitespace around its title.
     */
    private static NewPullRequest stripped(NewPullRequest pullRequest) {
        return new NewPullRequest(pullRequest.title().strip(), pullRequest.body(), pullRequest.draft());
    }

    /**
     * {@code popup} with {@code title} read for its base, as the title too.
     */
    private static NewPullRequestPopup titled(NewPullRequestPopup popup, String title) {
        NewPullRequest pullRequest = popup.pullRequest();
        return popup.withTitleRead(title)
                .withPullRequest(new NewPullRequest(title, pullRequest.body(), pullRequest.draft()));
    }

    /**
     * {@code model}, where the user moved in the list of bases of {@code popup} to {@code base}, if the filter shows
     * any: that is the base then, and the title is read for it while it is the one read before.
     */
    private static Next moved(Model model, NewPullRequestPopup popup, Option<BranchName> base) {
        return base.filter(picked -> !picked.equals(popup.base()))
                .map(picked -> {
                    Model picking = model.openPopup(popup.withBase(picked));
                    return popup.titleUntouched()
                            ? Next.of(picking, new RereadTitle(popup.branch(), picked, baseRef(model, picked)))
                            : Next.of(picking);
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Asks what the pull request of {@code branch}, which has none yet, merges into and what it says, as
     * {@link #ask} does, once it is on its remote with all its commits. Before that, the user is asked to push it
     * there, except when it has diverged from it: only a force push would, which is not done in passing.
     */
    public static Next askPushed(Model model, Branch branch) {
        return branch.remoteUpstream()
                .map(upstream -> branch.ahead() == 0
                        ? ask(model, branch)
                        : branch.behind() > 0
                                ? Next.of(model.fail(new BranchError.Diverged(branch.name(), upstream)))
                                : Update.ask(
                                        model, new PushFirstPopup(branch.name(), upstream, Option.of(branch.ahead()))))
                .getOrElse(
                        () -> Update.ask(model, new PushFirstPopup(branch.name(), branch.pushTarget(), Option.none())));
    }

    /**
     * The push of {@code branch} before its pull request is created: to the push target, which it then tracks, when
     * it has no upstream on a remote; fast-forward to its upstream when it is ahead of it. None when it has diverged
     * from it, or has nothing to push.
     */
    static Option<PushFirst> pushFirst(Branch branch) {
        return branch.remoteUpstream()
                .map(upstream -> branch.ahead() > 0 && branch.behind() == 0
                        ? Option.<PushCmd>of(new Push(branch.name(), upstream, PushMode.FAST_FORWARD))
                        : Option.<PushCmd>none())
                .getOrElse(() -> Option.of(new PushSettingUpstream(branch.name(), branch.pushTarget())))
                .map(PushFirst::new);
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
                        model, new ReadParent(branch.name(), bases.map(base -> new Base(base, baseRef(model, base)))));
    }

    /**
     * Reads the title the pull request of {@code branch} into the first of {@code bases} starts out with. The branch
     * can have another name on its remote; gh needs that one. Without bases, which the branches read while the
     * parent was can also leave, there is nothing to ask.
     */
    private static Next readTitle(Model model, Branch branch, List<BranchName> bases) {
        return bases.headOption()
                .map(first ->
                        Next.of(model, new ReadTitle(branch.name(), branch.pushTarget(), bases, baseRef(model, first))))
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
    private static BranchRef baseRef(Model model, BranchName base) {
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
