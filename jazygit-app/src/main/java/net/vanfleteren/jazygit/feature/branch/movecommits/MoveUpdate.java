package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.movecommits.MoveMsg.*;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.upstream.UpstreamUpdate;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Notice;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Moving the newest commits of the checked out branch to a new branch: reading which commits move, asking for the
 * name of the new branch, where it starts and, when some of the commits are pushed already, whether to force-push
 * the branch they are taken off; then moving them.
 */
public final class MoveUpdate {

    private MoveUpdate() {}

    public static Next update(Model model, MoveMsg msg) {
        return switch (msg) {
            case Requested(Sha sha) -> requested(model, sha);
            case RangeRead(var from, var oldest, var range) -> rangeRead(model, from, oldest, range);
            case RangeFailed(var from, var failure) ->
                Next.of(model.fail(new AppError.OperationFailed(MoveCmd.READ, Option.of(from.value()), failure)));
            case NameConfirmed(String name) -> named(model, name);
            case NameCancelled() -> Next.of(model.withoutPopup(MoveNamePopup.class));
            case BaseChosen(MoveBase base) ->
                model.popup(MoveBasePopup.class)
                        .map(popup ->
                                based(model.withoutPopup(MoveBasePopup.class), popup.request(), popup.name(), base))
                        .getOrElse(() -> Next.of(model));
            case BaseCancelled() -> Next.of(model.withoutPopup(MoveBasePopup.class));
            case PushedConfirmed(boolean forcePush) ->
                Update.confirm(
                        model,
                        MovePushedPopup.class,
                        popup -> Option.of(move(popup.request(), popup.name(), popup.base(), forcePush)));
            case PushedCancelled() -> Next.of(model.withoutPopup(MovePushedPopup.class));
            case Moved(var cmd, var commands) -> moved(model, cmd, commands);
            case Stopped(var cmd, var commands, var notice) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .inform(new Notice.MoveStopped(notice, cmd.from(), cmd.oldest())));
        };
    }

    /**
     * Reads which commits would move. That needs a checked out branch, which no operation runs on, and a commit
     * with a parent, where the branch is moved back to.
     */
    private static Next requested(Model model, Sha sha) {
        return model.repo()
                .currentBranch()
                .map(from -> model.idleBranch(from.name())
                        .flatMap(idle -> model.repo().commit(sha))
                        .filter(commit -> !commit.parents().isEmpty())
                        .map(commit -> Next.of(
                                model.clearError(),
                                new MoveCmd.ReadRange(
                                        from.name(), sha, from.remoteUpstream().map(Upstream::branch))))
                        .getOrElse(() -> Next.of(model)))
                .getOrElse(() -> Next.of(model.fail(new AppError.NoBranchCheckedOut())));
    }

    /**
     * Asks for the name of the new branch, unless the branch or the commit changed meanwhile.
     */
    private static Next rangeRead(Model model, Branch.Name from, Sha oldest, CommitRange range) {
        return model.repo()
                .currentBranch()
                .filter(current -> current.name().equals(from) && range.commits() > 0)
                .flatMap(current -> model.repo()
                        .commit(oldest)
                        .map(commit -> new MoveRequest(
                                from,
                                current.isDefault(),
                                current.remoteUpstream().map(Upstream::branch),
                                commit,
                                range,
                                bases(model, current, range))))
                .map(request -> Next.of(model.openPopupIfFree(new MoveNamePopup(request))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Where the new branch can start: where the commits are, and, unless they hold a merge, which does not
     * cherry-pick well, on the main branch or the upstream of {@code from}.
     */
    static List<MoveBase> bases(Model model, Branch from, CommitRange range) {
        List<BranchRef> others = range.merges() > 0
                ? List.empty()
                : UpstreamUpdate.base(model)
                        .toList()
                        .appendAll(from.upstream().map(upstream -> BranchRef.of(upstream.branch())))
                        .filter(ref -> !ref.equals(new BranchRef.Local(from.name())))
                        .distinct();
        return others.<MoveBase>map(MoveBase.Onto::new).prepend(new MoveBase.Parent());
    }

    /**
     * Asks where the new branch starts, when it can start elsewhere than where the commits are. A name that is no
     * branch name, or that of a branch that exists, keeps the dialog open.
     */
    private static Next named(Model model, String text) {
        return model.popup(MoveNamePopup.class)
                .flatMap(popup -> Branch.Name.parse(text)
                        .filter(name -> model.repo().branch(name).isEmpty())
                        .map(name -> {
                            Model closed = model.withoutPopup(MoveNamePopup.class);
                            return popup.request().bases().size() > 1
                                    ? Update.ask(closed, new MoveBasePopup(popup.request(), name))
                                    : based(closed, popup.request(), name, new MoveBase.Parent());
                        }))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Moves the commits, after asking about force-pushing when some of them are pushed already.
     */
    private static Next based(Model model, MoveRequest request, Branch.Name name, MoveBase base) {
        return request.pushed()
                ? Update.ask(model, new MovePushedPopup(request, name, base))
                : Update.start(model.clearError(), move(request, name, base, false));
    }

    private static MoveCmd.Move move(MoveRequest request, Branch.Name name, MoveBase base, boolean forcePush) {
        Option<BranchRef> onto = switch (base) {
            case MoveBase.Parent() -> Option.none();
            case MoveBase.Onto(BranchRef branch) -> Option.of(branch);
        };
        Option<RemoteBranch> pushTo = forcePush ? request.upstream() : Option.none();
        return new MoveCmd.Move(request.from(), request.oldest().sha(), name, onto, pushTo);
    }

    /**
     * Done as a checkout of the new branch; then force-pushes the branch the commits were taken off, if asked.
     */
    private static Next moved(Model model, MoveCmd.Move cmd, List<ExecutedCommand> commands) {
        Next checkedOut = new CheckoutMsg.CheckedOut(cmd, commands).apply(model);
        return cmd.forcePush()
                .map(upstream -> {
                    Next pushing = Update.start(
                            checkedOut.model(), new PushCmd.Push(cmd.from(), upstream, PushMode.FORCE_WITH_LEASE));
                    return new Next(pushing.model(), checkedOut.cmds().appendAll(pushing.cmds()));
                })
                .getOrElse(checkedOut);
    }
}
