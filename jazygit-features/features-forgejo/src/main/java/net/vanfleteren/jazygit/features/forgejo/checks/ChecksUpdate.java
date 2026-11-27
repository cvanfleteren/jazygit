package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoBuilds;
import net.vanfleteren.jazygit.features.forgejo.show.ForgejoPullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.edit.EditedFile;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.CheckList;
import net.vanfleteren.jazygit.kernel.state.popup.Covering;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Showing the checks on the tip of a branch on Forgejo one by one (ADR 195), as GitHub's are: reading them again by
 * hand and by itself (ADR 34); running the failed ones again (ADR 135); picking one, and opening its log in an editor
 * (ADR 160).
 */
public final class ChecksUpdate {

    private ChecksUpdate() {}

    public static Next update(Model model, ChecksMsg msg) {
        return switch (msg) {
            case ChecksMsg.Requested(BranchRef branch) ->
                onForgejo(model, branch)
                        .flatMap(remote -> tip(model, remote)
                                .map(tip -> Next.of(
                                        PopupUpdate.ask(
                                                        model,
                                                        ForgejoChecksPopup.loading(
                                                                branch, remote, tip, model.now(), Option.none()))
                                                .model(),
                                        new LoadChecks(remote, tip))))
                        .getOrElse(() -> Next.of(model));
            // Once its details are read; Escape goes back to it.
            case ChecksMsg.PullRequestChecksRequested() ->
                model.popup(ForgejoPullRequestPopup.class)
                        .filter(ChecksUpdate::ofPullRequest)
                        .flatMap(dialog -> dialog.shown().map(shown -> {
                            Sha tip = shown.pullRequest().branches().headSha();
                            return Next.of(
                                    PopupUpdate.ask(
                                                    model,
                                                    ForgejoChecksPopup.loading(
                                                            new BranchRef.Local(dialog.local()),
                                                            dialog.branch(),
                                                            tip,
                                                            model.now(),
                                                            Option.of(dialog)))
                                            .model(),
                                    new LoadChecks(dialog.branch(), tip));
                        }))
                        .getOrElse(() -> Next.of(model));
            case ChecksMsg.Closed() ->
                Next.of(model.popup(ForgejoChecksPopup.class)
                        .flatMap(ForgejoChecksPopup::from)
                        .map(model::openPopup)
                        .getOrElse(() -> model.withoutPopup(ForgejoChecksPopup.class)));
            case ChecksMsg.Read(RemoteBranch branch, var read) -> Next.of(shown(model, branch, read));
            case ChecksMsg.RefreshChosen() -> refresh(model);
            case ChecksMsg.Tick() -> readAgain(model, popup -> popup.dueByItself(model.now()), Reading.BY_ITSELF);
            case ChecksMsg.RerunRequested() -> rerunRequested(model);
            case ChecksMsg.RerunCancelled() ->
                Next.of(model.popup(RerunPopup.class)
                        .map(asking -> model.openPopup(asking.checks()))
                        .getOrElse(model));
            case ChecksMsg.RerunConfirmed() -> rerunConfirmed(model);
            case ChecksMsg.Rerun(RerunCmd cmd, var outcomes) -> rerun(model, cmd, outcomes);
            case ChecksMsg.Stepped(int by) -> Next.of(stepped(model, by));
            case ChecksMsg.Clicked(String name) ->
                Next.of(model.popup(ForgejoChecksPopup.class)
                        .flatMap(popup -> popup.checksRead()
                                .find(check -> check.name().equals(name))
                                .map(check -> model.openPopup(popup.withPicked(check))))
                        .getOrElse(model));
            case ChecksMsg.LogRequested() -> logRequested(model);
            case ChecksMsg.LogRead(CheckLogCmd cmd, String file, int line) -> logRead(model, cmd, file, line);
        };
    }

    /**
     * Whether the pull request {@code dialog} shows has checks to list: once its details are read, when the commit it
     * merges has statuses.
     */
    public static boolean ofPullRequest(ForgejoPullRequestPopup dialog) {
        return dialog.shown().exists(shown -> !shown.checks().statuses().isEmpty());
    }

    /**
     * The check {@code by} rows after the one picked picked, in the order they are listed, as far as there are; the
     * checks scrolled as little as it takes to show it (ADR 160).
     */
    private static Model stepped(Model model, int by) {
        return model.popup(ForgejoChecksPopup.class)
                .filter(popup -> !popup.checksRead().isEmpty())
                .map(popup -> {
                    List<ForgejoCheck> ordered = popup.ordered();
                    int at = popup.pickedCheck().map(ordered::indexOf).getOrElse(0);
                    int to = Math.clamp((long) at + by, 0, ordered.size() - 1);
                    return model.openPopup(popup.withPicked(ordered.get(to)))
                            .withListCursor(CheckList.scrolledTo(model.listCursor(), to, ordered.size()));
                })
                .getOrElse(model);
    }

    /**
     * Reads the log of the check picked, to open it in an editor (ADR 160); or says why it has none. Not before the
     * checks were read.
     */
    private static Next logRequested(Model model) {
        return model.popup(ForgejoChecksPopup.class)
                .flatMap(popup -> popup.pickedCheck()
                        .flatMap(check -> NoLog.of(check)
                                .map(why -> Next.of(
                                        model.openPopup(popup.withPicked(check).withNoLogAsked())))
                                .orElse(() -> check.job()
                                        .map(job -> OperationUpdate.start(
                                                model.openPopup(popup.withPicked(check)),
                                                new CheckLogCmd(popup.onForgejo(), check.name(), job))))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once the log of a check was read: the commands are logged, and it opens as a file does, by the feature that
     * opens files (ADR 145, 206): the menu of the editors, if it asks, goes over the checks and back to them.
     */
    private static Next logRead(Model model, CheckLogCmd cmd, String file, int line) {
        Model done = OperationUpdate.done(model, cmd, List.empty()).clearError();
        return Events.happened(
                Next.of(done),
                KernelEvent.FileToOpen.at(EditedFile.onDisk(file), line)
                        .titled(Phrase.of("forgejo.dialog.logEditor.title", cmd.name())));
    }

    /**
     * The checks shown read again as the user asked; while they are read by themselves, that read is shown as asked
     * for, rather than starting a second one.
     */
    private static Next refresh(Model model) {
        return model.popup(ForgejoChecksPopup.class)
                .filter(popup -> popup.reading() == Reading.BY_ITSELF)
                .map(popup -> Next.of(model.openPopup(popup.readingAgain(Reading.ASKED, popup.tip(), popup.readAt()))))
                .getOrElse(() -> readAgain(model, popup -> true, Reading.ASKED));
    }

    /**
     * Asks to run the failed checks shown again, once they were read; or says why nothing would be (ADR 135). Not
     * while those asked to run again before are still being run again.
     */
    private static Next rerunRequested(Model model) {
        return Next.of(model.popup(ForgejoChecksPopup.class)
                .filter(popup -> popup.read().isDefined())
                .filter(popup -> !popup.asked().exists(asked -> asked.pending(model.now())))
                .map(popup -> {
                    RerunPlan plan = RerunPlan.of(popup.checksRead());
                    return plan.runs().isEmpty()
                            ? model.openPopup(popup.withRerun(new RerunNote.Nothing(plan.nothing())))
                            : model.openPopup(new RerunPopup(popup, plan));
                })
                .getOrElse(model));
    }

    /**
     * Back to the checks, which say the failed ones were asked to run again, while Forgejo is asked to.
     */
    private static Next rerunConfirmed(Model model) {
        return model.popup(RerunPopup.class)
                .map(asking -> {
                    List<RerunPlan.Run> runs = asking.plan().runs();
                    ForgejoChecksPopup checks =
                            asking.checks().withRerun(RerunNote.Asked.at(model.now(), runs.map(RerunPlan.Run::run)));
                    return OperationUpdate.start(model.openPopup(checks), new RerunCmd(checks.onForgejo(), runs));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once Forgejo was asked to run the failed jobs again: a run it refused is shown as an error, and the checks popup,
     * when it still shows that branch, says how it went and reads the checks again at once, as {@code R} does; the
     * builds of the branches are read again, when a run was run again.
     */
    private static Next rerun(Model model, RerunCmd cmd, List<RerunCmd.Outcome> outcomes) {
        Model logged = OperationUpdate.done(model, cmd, List.empty());
        List<RerunCmd.Outcome> refused =
                outcomes.filter(outcome -> outcome.refused().isDefined());
        Model told = refused.flatMap(RerunCmd.Outcome::refused)
                .headOption()
                .map(failure -> logged.fail(new AppError.OperationFailed(
                        RerunCmd.RERUN,
                        Option.of(refused.map(outcome -> outcome.run().workflow())
                                .distinct()
                                .mkString(", ")),
                        failure)))
                .getOrElse(logged::clearError);
        Model builds =
                refused.size() < outcomes.size() ? told.mapPart(ForgejoBuilds.PART, ForgejoBuilds::fetched) : told;
        return told.popup(ForgejoChecksPopup.class)
                .filter(popup -> popup.onForgejo().equals(cmd.branch()))
                .flatMap(popup -> popup.asked()
                        .map(asked -> refresh(builds.openPopup(popup.withRerun(asked.answered(outcomes))))))
                // Over the checks, they say so once back to them, and read them again by themselves meanwhile.
                .orElse(() -> under(
                                builds,
                                cmd.branch(),
                                popup -> popup.asked()
                                        .map(asked -> popup.withRerun(asked.answered(outcomes)))
                                        .getOrElse(popup))
                        .map(Next::of))
                .getOrElse(() -> Next.of(builds));
    }

    /**
     * {@code model} reading the checks shown again, as {@code reading} tells, when they can be and {@code due}; else
     * as it is. Those of a branch are read on the tip it has now, as after a fetch or a push; those of a pull request
     * on the commit it merges, as Forgejo said, which its remote branch may not have caught up with.
     */
    private static Next readAgain(Model model, Predicate<ForgejoChecksPopup> due, Reading reading) {
        return model.popup(ForgejoChecksPopup.class)
                .filter(popup -> popup.refreshable() && due.test(popup))
                .map(popup -> {
                    Sha tip = popup.from().isDefined()
                            ? popup.tip()
                            : tip(model, popup.onForgejo()).getOrElse(popup.tip());
                    return Next.of(
                            model.openPopup(popup.readingAgain(reading, tip, model.now())),
                            new LoadChecks(popup.onForgejo(), tip));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The branch on Forgejo whose checks {@code branch} shows: itself when remote, else the one it follows; none when
     * that is not on Forgejo, or has no checks, as its badge tells.
     */
    private static Option<RemoteBranch> onForgejo(Model model, BranchRef branch) {
        ForgejoBuilds builds = model.part(ForgejoBuilds.PART);
        return (switch (branch) {
                    case BranchRef.Local(BranchName name) ->
                        model.repo()
                                .branches()
                                .toOption()
                                .flatMap(branches ->
                                        branches.find(local -> local.name().equals(name)))
                                .flatMap(Branch::remoteUpstream);
                    case BranchRef.Remote(RemoteBranch remote) -> Option.of(remote);
                })
                .filter(remote -> !builds.elsewhere().contains(remote.remote()))
                .filter(remote ->
                        tip(model, remote).flatMap(builds::state).exists(state -> state != CommitStatus.State.NONE));
    }

    /**
     * The commit {@code branch} points to, as far as the repository knows it.
     */
    private static Option<Sha> tip(Model model, RemoteBranch branch) {
        return model.repo()
                .remoteBranches()
                .toOption()
                .flatMap(refs -> refs.find(ref -> ref.branch().equals(branch)))
                .map(RemoteRef::tip);
    }

    /**
     * {@code model} showing what was read of the checks on {@code branch}; unchanged when they are no longer shown.
     */
    private static Model shown(Model model, RemoteBranch branch, Either<Failure, ChecksRead> read) {
        return model.popup(ForgejoChecksPopup.class)
                .filter(popup -> popup.onForgejo().equals(branch))
                .map(popup -> model.openPopup(popup.with(read)))
                .orElse(() -> under(model, branch, popup -> popup.with(read)))
                .getOrElse(model);
    }

    /**
     * {@code model} with the checks popup of {@code branch} under the menu to rerun or open a log in changed as
     * {@code change} says; none when there is none.
     */
    private static Option<Model> under(Model model, RemoteBranch branch, UnaryOperator<ForgejoChecksPopup> change) {
        return model.popup(RerunPopup.class)
                .filter(asking -> asking.checks().onForgejo().equals(branch))
                .map(asking -> model.openPopup(asking.over(change.apply(asking.checks()))))
                .orElse(() -> model.popup(Covering.class)
                        .flatMap(asking -> asking.under()
                                .filter(ForgejoChecksPopup.class::isInstance)
                                .map(ForgejoChecksPopup.class::cast)
                                .filter(checks -> checks.onForgejo().equals(branch))
                                .map(checks -> model.openPopup(asking.over(change.apply(checks))))));
    }
}
