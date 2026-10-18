package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.movecommits.MoveMsg.*;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.upstream.UpstreamUpdate;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
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
 * Moving the newest commits of the checked out branch to another branch: reading which commits move, asking which
 * branch they move to, or the name of a new branch and where it starts, and, when some of the commits are pushed
 * already, whether to force-push the branch they are taken off; then moving them.
 */
public final class MoveUpdate {

    private MoveUpdate() {}

    public static Next update(Model model, MoveMsg msg) {
        return switch (msg) {
            case Requested(Sha sha) -> requested(model, sha);
            case RangeRead(var from, var oldest, var range) -> rangeRead(model, from, oldest, range);
            case RangeFailed(var from, var failure) ->
                Next.of(model.fail(new AppError.OperationFailed(MoveCmd.READ, Option.of(from.value()), failure)));
            case TargetChosen(var branch) ->
                model.popup(MoveTargetPopup.class)
                        .map(popup -> targetChosen(model.withoutPopup(MoveTargetPopup.class), popup.request(), branch))
                        .getOrElse(() -> Next.of(model));
            case TargetCancelled() -> Next.of(model.withoutPopup(MoveTargetPopup.class));
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
                        popup -> Option.of(move(popup.request(), popup.target(), forcePush)));
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
     * Asks where the commits move to, unless the branch or the commit changed meanwhile. Commits that hold a merge
     * can only move to a new branch where they are, as they do not cherry-pick well, so then its name is asked
     * straight away.
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
                .map(request -> Next.of(model.openPopupIfFree(
                        range.merges() > 0
                                ? new MoveNamePopup(request)
                                : new MoveTargetPopup(request, others(model, from)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The local branches the commits can move to: the ones other than {@code from}.
     */
    private static List<Branch> others(Model model, Branch.Name from) {
        return model.repo()
                .branches()
                .toOption()
                .getOrElse(List.empty())
                .filter(branch -> !branch.name().equals(from));
    }

    /**
     * Asks for the name of a new branch, or moves the commits onto the existing {@code branch}. One that is checked
     * out in another worktree cannot be checked out here, and one that an operation runs on takes no other.
     */
    private static Next targetChosen(Model model, MoveRequest request, Option<Branch.Name> branch) {
        return branch.fold(
                () -> Update.ask(model, new MoveNamePopup(request)),
                name -> model.repo()
                        .branch(name)
                        .map(known -> known.worktreeLocation()
                                .map(worktree -> Next.of(
                                        model.fail(new AppError.CheckedOutElsewhere(MoveCmd.MOVE, name, worktree))))
                                .getOrElse(() -> model.idleBranch(name)
                                        .map(idle -> targeted(model, request, new MoveTarget.Existing(name)))
                                        .getOrElse(() -> Next.of(model))))
                        .getOrElse(() -> Next.of(model)));
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
     * Moves the commits to the new branch {@code name}, which starts at {@code base}.
     */
    private static Next based(Model model, MoveRequest request, Branch.Name name, MoveBase base) {
        Option<BranchRef> onto = switch (base) {
            case MoveBase.Parent() -> Option.none();
            case MoveBase.Onto(BranchRef branch) -> Option.of(branch);
        };
        return targeted(model, request, new MoveTarget.NewBranch(name, onto));
    }

    /**
     * Moves the commits to {@code target}, after asking about force-pushing when some of them are pushed already.
     * Commits that are copied are not moved while the working tree has changes: git refuses to cherry-pick onto
     * staged changes, or onto changes to the files the commits touch, which would leave the target checked out and
     * the commits unmoved. Untracked files do not count, as git only stops for those that are in the way.
     */
    private static Next targeted(Model model, MoveRequest request, MoveTarget target) {
        boolean changed = Update.files(model).exists(file -> file.type() != ChangeType.UNTRACKED);
        return copiedOnto(target)
                .filter(onto -> changed)
                .map(onto -> Next.of(model.fail(new AppError.ChangesBeforeMove(onto))))
                .getOrElse(() -> request.pushed()
                        ? Update.ask(model, new MovePushedPopup(request, target))
                        : Update.start(model.clearError(), move(request, target, false)));
    }

    /**
     * The branch the commits are copied onto, by name: an existing one, or the one a new branch starts at elsewhere;
     * none when they stay where they are.
     */
    private static Option<String> copiedOnto(MoveTarget target) {
        return switch (target) {
            case MoveTarget.NewBranch(var branch, var onto) -> onto.map(BranchRef::name);
            case MoveTarget.Existing(var branch) -> Option.of(branch.value());
        };
    }

    private static MoveCmd.Move move(MoveRequest request, MoveTarget target, boolean forcePush) {
        Option<RemoteBranch> pushTo = forcePush ? request.upstream() : Option.none();
        return new MoveCmd.Move(request.from(), request.oldest().sha(), target, pushTo);
    }

    /**
     * Done as a checkout of the branch the commits moved to; then force-pushes the branch the commits were taken off, if asked.
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
