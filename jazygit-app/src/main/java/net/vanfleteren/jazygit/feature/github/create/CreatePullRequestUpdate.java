package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.Base;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadParent;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

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
            // The branch may have gone while its parent was read; then there is nothing to ask.
            case ParentRead(var branch, var parent) ->
                model.repo()
                        .branch(branch)
                        .map(known -> readTitle(model, known, parentFirst(bases(model, branch), parent)))
                        .getOrElse(() -> Next.of(model));
            // Without a commit of its own to name it after, it is named after the branch, as gh would.
            case TitleRead(var branch, var head, var bases, var subject) -> {
                String title = subject.getOrElse(branch::value);
                yield Next.of(model.clearError()
                        .openPopupIfFree(new NewPullRequestPopup(
                                branch, head, bases, bases.head(), title, new NewPullRequest(title, "", false))));
            }
            case BaseMoved(var cursor, var base) ->
                model.popup(NewPullRequestPopup.class)
                        .map(popup -> moved(model.withListCursor(cursor), popup, base))
                        .getOrElse(() -> Next.of(model));
            // A title read for a base no longer picked, or after the user typed one, is dropped.
            case BaseTitleRead(var branch, var base, var subject) ->
                Next.of(model.popup(NewPullRequestPopup.class)
                        .filter(popup ->
                                popup.branch().equals(branch) && popup.base().equals(base) && popup.titleUntouched())
                        .map(popup -> model.openPopup(titled(popup, subject.getOrElse(branch::value))))
                        .getOrElse(model));
            case PullRequestEdited(NewPullRequest pullRequest) ->
                Next.of(model.popup(NewPullRequestPopup.class)
                        .map(popup -> model.openPopup(popup.withPullRequest(pullRequest)))
                        .getOrElse(model));
            // A blank title keeps the popup open, as GitHub needs one.
            case Confirmed()
            when model.popup(NewPullRequestPopup.class)
                    .exists(popup -> popup.pullRequest().title().isBlank()) -> Next.of(model);
            case Confirmed() ->
                Update.confirm(
                        model,
                        NewPullRequestPopup.class,
                        popup -> model.repo()
                                .branch(popup.branch())
                                .map(known -> new CreatePullRequest(
                                        popup.branch(),
                                        popup.head(),
                                        new Base(popup.base(), baseRef(model, popup.base())).remoteName(),
                                        stripped(popup.pullRequest()))));
            // The branches may have been read again while the user was asked: with nothing to push any more, it was
            // pushed meanwhile or has diverged since, so it is asked about again as it is now.
            case PushFirstConfirmed() ->
                model.popup(PushFirstPopup.class)
                        .flatMap(popup -> model.idleBranch(popup.branch()))
                        .filter(known -> pushFirst(known).isEmpty())
                        .map(known -> askPushed(model.withoutPopup(PushFirstPopup.class), known))
                        .getOrElse(() -> Update.confirm(
                                model,
                                PushFirstPopup.class,
                                popup -> model.idleBranch(popup.branch()).flatMap(CreatePullRequestUpdate::pushFirst)));
            // Done like any push. Asking needs the other branches and the name the branch has on its remote, which is
            // where it was just pushed to, so it does not wait for the branches to be read again.
            case Pushed(PushFirst cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                yield done.model()
                        .repo()
                        .branch(cmd.branch())
                        .map(known -> ask(done.model(), known))
                        .map(asked -> new Next(asked.model(), done.cmds().appendAll(asked.cmds())))
                        .getOrElse(done);
            }
            case Created(CreatePullRequest cmd, var pullRequest, var commands) -> {
                // Done like any operation. The new pull request shows at once, as GitHub may not list it yet;
                // reading the pull requests then gives its checks and review.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model shown = pullRequest
                        .map(pr -> done.model()
                                .mapGitHub(gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                                        .map(open -> open.put(new BranchName(pr.headBranch()), pr)))))
                        .getOrElse(done.model());
                // A read that is still running may have missed it, so this one is started anyway. GitHub may have
                // changed the remote too, which a fetch brings in.
                yield FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(shown, done.cmds())));
            }
        };
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
                                ? Next.of(model.fail(AppError.refused(
                                        "error.pullRequest.diverged",
                                        branch.name().value(),
                                        upstream.shortName())))
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
                .map(first -> Next.of(
                        model,
                        new ReadTitle(branch.name(), branch.pushTarget().branch(), bases, baseRef(model, first))))
                .getOrElse(() -> Next.of(model.fail(AppError.refused(
                        "error.pullRequest.noBase", branch.name().value()))));
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
