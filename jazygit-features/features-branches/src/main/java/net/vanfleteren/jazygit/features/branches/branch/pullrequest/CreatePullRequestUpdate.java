package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Creating a pull request, as far as it is the same on every forge: pushing the branch first when it is not on its
 * remote with all its commits (ADR 25), then, once the forge asks, opening its popup with the title read, where the
 * title follows the base picked until the user types one (ADR 18).
 */
public final class CreatePullRequestUpdate {

    private CreatePullRequestUpdate() {}

    public static Next update(Model model, CreatePullRequestMsg msg) {
        return switch (msg) {
            case PushFirstConfirmed() -> pushFirstConfirmed(model);
            case Pushed(var cmd, var commands) -> pushed(model, cmd, commands);
            case TitleRead(var branch, var opening, var subject) -> titleRead(model, branch, opening, subject);
            case BaseMoved(var cursor, var base) -> baseMoved(model, cursor, base);
            case BaseTitleRead(var branch, var base, var subject) -> baseTitleRead(model, branch, base, subject);
        };
    }

    /**
     * Goes on with {@code branch} as {@code then} says once it is on its remote with all its commits. Before that,
     * the user is asked to push it there, except when it has diverged from it: only a force push would, which is not
     * done in passing.
     */
    public static Next askPushed(Model model, Branch branch, AfterPush then) {
        return branch.remoteUpstream()
                .map(upstream -> branch.ahead() == 0
                        ? then.next(model, branch)
                        : branch.behind() > 0
                                ? Next.of(model.fail(new BranchError.Diverged(branch.name(), upstream)))
                                : PopupUpdate.ask(
                                        model,
                                        new PushFirstPopup(branch.name(), upstream, Option.of(branch.ahead()), then)))
                .getOrElse(() -> PopupUpdate.ask(
                        model, new PushFirstPopup(branch.name(), branch.pushTarget(), Option.none(), then)));
    }

    /**
     * The push of {@code branch} before its pull request is created: to the push target, which it then tracks, when
     * it has no upstream on a remote; fast-forward to its upstream when it is ahead of it. None when it has diverged
     * from it, or has nothing to push.
     */
    static Option<PushFirst> pushFirst(Branch branch, AfterPush then) {
        return branch.remoteUpstream()
                .map(upstream -> branch.ahead() > 0 && branch.behind() == 0
                        ? Option.<PushCmd>of(new Push(branch.name(), upstream, PushMode.FAST_FORWARD))
                        : Option.<PushCmd>none())
                .getOrElse(() -> Option.of(new PushSettingUpstream(branch.name(), branch.pushTarget())))
                .map(push -> new PushFirst(push, then));
    }

    /**
     * Pushes the branch before its pull request is created. The branches may have been read again while the user
     * was asked: with nothing to push any more, it was pushed meanwhile or has diverged since, so it is asked about
     * again as it is now.
     */
    private static Next pushFirstConfirmed(Model model) {
        return model.popup(PushFirstPopup.class)
                .flatMap(popup -> model.idleBranch(popup.branch())
                        .filter(known -> pushFirst(known, popup.then()).isEmpty())
                        .map(known -> askPushed(model.withoutPopup(PushFirstPopup.class), known, popup.then())))
                .getOrElse(() -> PopupUpdate.confirm(
                        model,
                        PushFirstPopup.class,
                        popup -> model.idleBranch(popup.branch()).flatMap(known -> pushFirst(known, popup.then()))));
    }

    /**
     * Done like any push, then on as the push says, without waiting for the branches to be read again: what comes
     * next needs the other branches and the name the branch has on its remote, which is where it was just pushed to.
     */
    private static Next pushed(Model model, PushFirst cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> done.repo()
                        .branch(cmd.branch())
                        .map(known -> cmd.then().next(done, known))
                        .getOrElse(() -> Next.of(done)));
    }

    /**
     * Opens the popup with the title read. Without a commit of its own to name it after, it is named after the
     * branch. Another popup that opened meanwhile is left open.
     */
    private static Next titleRead(Model model, BranchName branch, FormOpening opening, Option<String> subject) {
        return Next.of(model.clearError().openPopupIfFree(opening.titled(subject.getOrElse(branch::value))));
    }

    /**
     * Moves in the list of bases to {@code cursor}: the highlighted base, if the filter shows any, is the base then,
     * and the title is read for it while it is the one read before.
     */
    private static Next baseMoved(Model model, ListCursor cursor, Option<BranchName> base) {
        return model.popup(NewPullRequestForm.class)
                .map(form -> {
                    Model moved = model.withListCursor(cursor);
                    return base.filter(picked -> !picked.equals(form.base()))
                            .map(picked -> {
                                Model picking = moved.openPopup(form.withBase(picked));
                                return form.titleUntouched()
                                        ? form.baseRef(model, picked)
                                                .map(ref ->
                                                        Next.of(picking, new RereadTitle(form.branch(), picked, ref)))
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
        return Next.of(model.popup(NewPullRequestForm.class)
                .filter(form -> form.branch().equals(branch) && form.base().equals(base) && form.titleUntouched())
                .map(form -> model.openPopup(form.titled(subject.getOrElse(branch::value))))
                .getOrElse(model));
    }
}
