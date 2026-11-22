package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CheckHead;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Creating the pull request on Forgejo of a branch that has none (ADR 178): checking that it is pushed to a remote
 * on the host of the home, pushing it first when it is not on its remote, reading the title it starts out with, then
 * asking what it merges into and what it says, in one popup, where the title follows the base until the user types
 * one.
 */
public final class CreatePullRequestUpdate {

    // A remote's own pointer to its default branch, which is no branch to merge into.
    private static final BranchName REMOTE_HEAD = new BranchName("HEAD");

    private CreatePullRequestUpdate() {}

    public static Next update(Model model, CreatePullRequestMsg msg) {
        return switch (msg) {
            case HeadChecked(var branch, var error) -> headChecked(model, branch, error);
            case PushFirstConfirmed() -> pushFirstConfirmed(model);
            case Pushed(var cmd, var commands) -> pushed(model, cmd, commands);
            case TitleRead(var branch, var bases, var subject) -> titleRead(model, branch, bases, subject);
            case BaseMoved(var cursor, var base) -> baseMoved(model, cursor, base);
            case BaseTitleRead(var branch, var base, var subject) -> baseTitleRead(model, branch, base, subject);
            case Edited(var title, var body, var draft) -> edited(model, title, body, draft);
            case Confirmed() -> confirmed(model);
            case Created(var cmd, var created) -> created(model, cmd, created);
            case AlreadyOpen(var cmd, var number) -> alreadyOpen(model, cmd, number);
        };
    }

    /**
     * Starts creating the pull request of {@code branch} into {@code home}, which has none: where it is pushed to is
     * checked first, before anything is pushed or asked.
     */
    static Next start(Model model, Branch branch, RemoteName home) {
        return Next.of(model.clearError(), new CheckHead(home, branch.name(), branch.pushTarget()));
    }

    /**
     * Once where it is pushed to was checked: why it cannot be created there, or on to pushing it. The branch may have
     * gone, or become busy, meanwhile; then nothing happens.
     */
    private static Next headChecked(Model model, BranchName branch, Option<CreateError> error) {
        return error.map(why -> Next.of(model.fail(why)))
                .getOrElse(() -> model.idleBranch(branch)
                        .map(known -> askPushed(model, known))
                        .getOrElse(() -> Next.of(model)));
    }

    /**
     * Asks what the pull request of {@code branch} says, as {@link #ask} does, once it is on its remote with all its
     * commits (ADR 25). Before that, the user is asked to push it there, except when it has diverged from it: only a
     * force push would, which is not done in passing.
     */
    static Next askPushed(Model model, Branch branch) {
        return branch.remoteUpstream()
                .map(upstream -> branch.ahead() == 0
                        ? ask(model, branch)
                        : branch.behind() > 0
                                ? Next.of(model.fail(new BranchError.Diverged(branch.name(), upstream)))
                                : PopupUpdate.ask(
                                        model, new PushFirstPopup(branch.name(), upstream, Option.of(branch.ahead()))))
                .getOrElse(() ->
                        PopupUpdate.ask(model, new PushFirstPopup(branch.name(), branch.pushTarget(), Option.none())));
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
     * Pushes the branch before its pull request is created. The branches may have been read again while the user
     * was asked: with nothing to push any more, it was pushed meanwhile or has diverged since, so it is asked about
     * again as it is now.
     */
    private static Next pushFirstConfirmed(Model model) {
        return model.popup(PushFirstPopup.class)
                .flatMap(popup -> model.idleBranch(popup.branch()))
                .filter(known -> pushFirst(known).isEmpty())
                .map(known -> askPushed(model.withoutPopup(PushFirstPopup.class), known))
                .getOrElse(() -> PopupUpdate.confirm(
                        model,
                        PushFirstPopup.class,
                        popup -> model.idleBranch(popup.branch()).flatMap(CreatePullRequestUpdate::pushFirst)));
    }

    /**
     * Done like any push, then on to asking what the pull request says, without waiting for the branches to be read
     * again.
     */
    private static Next pushed(Model model, PushFirst cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> done.repo()
                        .branch(cmd.branch())
                        .map(known -> ask(done, known))
                        .getOrElse(() -> Next.of(done)));
    }

    /**
     * Asks what the pull request of {@code branch} merges into and what it says, once the title it starts out with
     * is read for the base offered first. With no branch of the home's remote to merge into, there is nothing to ask.
     */
    static Next ask(Model model, Branch branch) {
        return ForgeHome.of(model)
                .map(home -> {
                    List<BranchName> bases = bases(model, home, branch);
                    return bases.headOption()
                            .map(first -> Next.of(model, new ReadTitle(branch.name(), bases, baseRef(home, first))))
                            .getOrElse(() -> Next.of(model.fail(new CreateError.NoBase(branch.name()))));
                })
                .getOrElse(() -> Next.of(model.fail(new CreateError.NoHome())));
    }

    /**
     * The branches of the home's remote {@code home} that a pull request from {@code branch} can merge into (ADR
     * 178): all but the one it is pushed to, its recorded parent first, then the one of the name of the default
     * branch.
     */
    static List<BranchName> bases(Model model, RemoteName home, Branch branch) {
        List<BranchName> names = model.repo()
                .remoteBranches()
                .toOption()
                .getOrElse(List.empty())
                .map(RemoteRef::branch)
                .filter(remote -> remote.remote().equals(home)
                        && !remote.equals(branch.pushTarget())
                        && !remote.branch().equals(REMOTE_HEAD))
                .map(RemoteBranch::branch)
                .distinct();
        Option<BranchName> main = model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(Branch::isDefault))
                .map(Branch::name);
        return first(first(names, main), model.repo().parents().get(branch.name()));
    }

    /**
     * {@code bases} with {@code first} first, if it is one of them.
     */
    private static List<BranchName> first(List<BranchName> bases, Option<BranchName> first) {
        return first.filter(bases::contains)
                .map(found -> bases.remove(found).prepend(found))
                .getOrElse(bases);
    }

    private static BranchRef baseRef(RemoteName home, BranchName base) {
        return new BranchRef.Remote(new RemoteBranch(home, base));
    }

    /**
     * Opens the popup with the title read. Without a commit of its own to name it after, it is named after the
     * branch.
     */
    private static Next titleRead(Model model, BranchName branch, List<BranchName> bases, Option<String> subject) {
        return Next.of(model.clearError()
                .openPopupIfFree(NewPullRequestPopup.opening(branch, bases, subject.getOrElse(branch::value))));
    }

    /**
     * Moves in the list of bases to {@code cursor}: the highlighted base, if the filter shows any, is the base then,
     * and the title is read for it while it is the one read before.
     */
    private static Next baseMoved(Model model, ListCursor cursor, Option<BranchName> base) {
        return model.popup(NewPullRequestPopup.class)
                .map(popup -> {
                    Model moved = model.withListCursor(cursor);
                    return base.filter(picked -> !picked.equals(popup.base()))
                            .map(picked -> {
                                Model picking = moved.openPopup(popup.withBase(picked));
                                return popup.titleUntouched()
                                        ? ForgeHome.of(model)
                                                .map(home -> Next.of(
                                                        picking,
                                                        new RereadTitle(popup.branch(), picked, baseRef(home, picked))))
                                                .getOrElse(() -> Next.of(picking))
                                        : Next.of(picking);
                            })
                            .getOrElse(() -> Next.of(moved));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Takes the title read for {@code base}. A title read for a base no longer picked, or after the user typed one,
     * is dropped.
     */
    private static Next baseTitleRead(Model model, BranchName branch, BranchName base, Option<String> subject) {
        return Next.of(model.popup(NewPullRequestPopup.class)
                .filter(popup -> popup.branch().equals(branch) && popup.base().equals(base) && popup.titleUntouched())
                .map(popup -> {
                    String title = subject.getOrElse(branch::value);
                    return model.openPopup(popup.withTitleRead(title).withTitle(title));
                })
                .getOrElse(model));
    }

    private static Next edited(Model model, String title, String body, boolean draft) {
        return Next.of(model.popup(NewPullRequestPopup.class)
                .map(popup ->
                        model.openPopup(popup.withTitle(title).withBody(body).withDraft(draft)))
                .getOrElse(model));
    }

    /**
     * Creates the pull request the popup describes. A blank title keeps the popup open, as Forgejo needs one.
     */
    private static Next confirmed(Model model) {
        if (model.popup(NewPullRequestPopup.class).exists(popup -> popup.title().isBlank())) {
            return Next.of(model);
        }
        return ForgeHome.of(model)
                .map(home -> PopupUpdate.confirm(
                        model,
                        NewPullRequestPopup.class,
                        popup -> model.repo()
                                .branch(popup.branch())
                                .map(known -> new CreatePullRequest(
                                        home,
                                        popup.branch(),
                                        known.pushTarget(),
                                        popup.base(),
                                        popup.title().strip(),
                                        popup.body(),
                                        popup.draft()))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Done like any operation: the new pull request is the branch's at once, and the pull requests are read again,
     * as after any operation that changes what Forgejo has.
     */
    private static Next created(Model model, CreatePullRequest cmd, ForgejoPullRequest created) {
        return OperationUpdate.succeeded(model, cmd, List.empty())
                .map(done ->
                        done.mapPart(ForgejoPullRequests.PART, prs -> prs.created(cmd.home(), cmd.target(), created)));
    }

    /**
     * Failed as the branch has a pull request open already, which the pull requests read before did not have: they
     * are read again, so that {@code o} shows it next.
     */
    private static Next alreadyOpen(Model model, CreatePullRequest cmd, long number) {
        return OperationUpdate.update(
                        model, new OperationMsg.Failed(cmd, new CreateFailure.AlreadyOpen(cmd.branch(), number)))
                .and(new ReadPullRequests(cmd.home()));
    }
}
