package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.changes.edit.EditCmd;
import net.vanfleteren.jazygit.features.changes.edit.EditorSettings;
import net.vanfleteren.jazygit.features.changes.edit.OpenFileCmd;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.BranchChecksUpdate;
import net.vanfleteren.jazygit.kernel.state.github.CheckTimesUpdate;
import net.vanfleteren.jazygit.kernel.state.github.FromGitHub;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.github.PendingChecks;
import net.vanfleteren.jazygit.kernel.state.github.Reading;
import net.vanfleteren.jazygit.kernel.state.operation.CommandLog;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Showing the checks on the tip of a branch one by one, with how long they took or run, and reading them again by
 * hand and by itself, as the pull request dialog does (ADR 19, ADR 32, ADR 34); running the failed ones again
 * (ADR 135); picking one, and opening its log in an editor (ADR 160).
 */
public final class ChecksUpdate {

    private ChecksUpdate() {}

    public static Next update(Model model, ChecksMsg msg) {
        return switch (msg) {
            case ChecksMsg.Requested(BranchRef branch) ->
                onGitHub(model, branch)
                        .map(remote -> Next.of(
                                PopupUpdate.ask(model, ChecksPopup.loading(branch, remote, model.now()))
                                        .model(),
                                new LoadChecks(remote)))
                        .getOrElse(() -> Next.of(model));
            // Once its details are read, as viewing its comments; Escape goes back to it.
            case ChecksMsg.PullRequestChecksRequested() ->
                model.popup(PullRequestPopup.class)
                        .flatMap(dialog -> ofPullRequest(model, dialog)
                                .map(remote -> Next.of(
                                        PopupUpdate.ask(
                                                        model,
                                                        ChecksPopup.loading(
                                                                remote.tracking(), remote, model.now(), dialog))
                                                .model(),
                                        new LoadChecks(remote))))
                        .getOrElse(() -> Next.of(model));
            case ChecksMsg.Closed() ->
                Next.of(model.popup(ChecksPopup.class)
                        .flatMap(ChecksPopup::from)
                        .map(model::openPopup)
                        .getOrElse(() -> model.withoutPopup(ChecksPopup.class)));
            // How long checks take is learned from them too, and the branch catches up when it shows them otherwise.
            case ChecksMsg.Loaded(RemoteBranch branch, PendingChecks checks) ->
                CheckTimesUpdate.read(
                        caughtUp(
                                Next.of(shown(model, branch, Either.right(checks))),
                                branch,
                                checks.of(branch.branch())),
                        checks.repository(),
                        checks.of(branch.branch()));
            case ChecksMsg.Failed(RemoteBranch branch, Failure failure) ->
                Next.of(shown(model, branch, Either.left(failure)));
            case ChecksMsg.RefreshChosen() -> refresh(model);
            case ChecksMsg.Tick() -> readAgain(model, popup -> popup.dueByItself(model.now()), Reading.BY_ITSELF);
            case ChecksMsg.RerunRequested() -> rerunRequested(model);
            case ChecksMsg.RerunCancelled() ->
                Next.of(model.popup(RerunPopup.class)
                        .map(asking -> model.openPopup(asking.checks()))
                        .getOrElse(model));
            case ChecksMsg.RerunConfirmed() -> rerunConfirmed(model);
            case ChecksMsg.Rerun(RerunCmd cmd, List<RerunCmd.Outcome> outcomes, var commands) ->
                rerun(model, cmd, outcomes, commands);
            case ChecksMsg.Stepped(int by) -> Next.of(stepped(model, by));
            case ChecksMsg.Clicked(CheckPick pick) ->
                Next.of(model.popup(ChecksPopup.class)
                        .flatMap(popup -> popup.checksRead()
                                .find(pick::matches)
                                .map(check -> model.openPopup(popup.withPicked(check))))
                        .getOrElse(model));
            case ChecksMsg.LogRequested() -> logRequested(model);
            case ChecksMsg.LogRead(CheckLogCmd cmd, String file, int line, var commands) ->
                logRead(model, cmd, file, line, commands);
            case ChecksMsg.LogEditorChosen(EditorProgram editor) ->
                model.popup(LogEditorPopup.class)
                        .map(asking -> OperationUpdate.start(
                                asking.back(model), new OpenFileCmd(editor, asking.file(), asking.line())))
                        .getOrElse(() -> Next.of(model));
            case ChecksMsg.LogEditorCancelled() ->
                Next.of(model.popup(LogEditorPopup.class)
                        .map(asking -> asking.back(model))
                        .getOrElse(model));
        };
    }

    /**
     * The check {@code by} rows after the one picked picked, in the order they are listed, as far as there are; the
     * checks scrolled as little as it takes to show it (ADR 160).
     */
    private static Model stepped(Model model, int by) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> !popup.checksRead().isEmpty())
                .map(popup -> {
                    List<Check> ordered = popup.ordered(model);
                    int at = popup.pickedCheck(model).map(ordered::indexOf).getOrElse(0);
                    int to = Math.clamp((long) at + by, 0, ordered.size() - 1);
                    ListCursor cursor = model.listCursor();
                    int last = Math.max(0, ordered.size() - ChecksPopup.VISIBLE_CHECKS);
                    int first = Math.clamp(cursor.line().getOrElse(0), 0, last);
                    int shown = Math.clamp(first, Math.max(0, to - ChecksPopup.VISIBLE_CHECKS + 1), Math.min(to, last));
                    return model.openPopup(popup.withPicked(ordered.get(to))).withListCursor(cursor.at(shown));
                })
                .getOrElse(model);
    }

    /**
     * Reads the log of the check picked, to open it in an editor (ADR 160); or says why it has none. Not before the
     * checks were read.
     */
    private static Next logRequested(Model model) {
        return model.popup(ChecksPopup.class)
                .flatMap(popup -> popup.pickedCheck(model)
                        .flatMap(check -> NoLog.of(check)
                                .map(why -> Next.of(
                                        model.openPopup(popup.withPicked(check).withNoLogAsked())))
                                .orElse(() -> check.run()
                                        .flatMap(run -> popup.repository()
                                                .map(repository -> OperationUpdate.start(
                                                        model.openPopup(popup.withPicked(check)),
                                                        new CheckLogCmd(repository, check.name(), run)))))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once the log of a check was read: the commands are logged, and it opens as a file does (ADR 145): in git's editor
     * without an editor set to open files, at its start; else in the one set, or the one chosen in a menu of them, over
     * the checks popup; in the first of them when another popup is over the checks, which the menu would take away.
     */
    private static Next logRead(Model model, CheckLogCmd cmd, String file, int line, List<ExecutedCommand> commands) {
        Model done = OperationUpdate.done(model, cmd, commands).clearError();
        Editors editors =
                done.preferences().settings().get(EditorSettings.EDITORS).files();
        if (editors.isEmpty()) {
            return OperationUpdate.start(done, new EditCmd(List.of(file)));
        }
        if (editors.all().size() == 1) {
            return OperationUpdate.start(done, new OpenFileCmd(editors.all().head(), file, line));
        }
        Option<ChecksPopup> checks = done.popup(ChecksPopup.class);
        if (checks.isEmpty() && done.popup().isDefined()) {
            // Another popup is over the checks, which the menu would take away: the first editor opens it.
            return OperationUpdate.start(done, new OpenFileCmd(editors.all().head(), file, line));
        }
        return Next.of(done.openPopup(
                new LogEditorPopup(checks, done.listCursor(), cmd.name(), file, line, editors.lettered())));
    }

    /**
     * The checks shown read again as the user asked; while they are read by themselves, that read is shown as asked
     * for, rather than starting a second one.
     */
    private static Next refresh(Model model) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.reading() == Reading.BY_ITSELF)
                .map(popup -> Next.of(model.openPopup(popup.readingAgain(Reading.ASKED, popup.readAt()))))
                .getOrElse(() -> readAgain(model, popup -> true, Reading.ASKED));
    }

    /**
     * Asks to run the failed checks shown again, once they were read; or says why nothing would be (ADR 135). Not
     * while those asked to run again before are still being run again: the checks shown may still show them failed and
     * done, and the title says they were asked to.
     */
    private static Next rerunRequested(Model model) {
        return Next.of(model.popup(ChecksPopup.class)
                .filter(ChecksPopup::wasRead)
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
     * Back to the checks, which say the failed ones were asked to run again, while GitHub is asked to.
     */
    private static Next rerunConfirmed(Model model) {
        return model.popup(RerunPopup.class)
                .map(asking -> {
                    List<RerunPlan.Run> runs = asking.plan().runs();
                    ChecksPopup checks =
                            asking.checks().withRerun(RerunNote.Asked.at(model.now(), runs.map(RerunPlan.Run::runId)));
                    return OperationUpdate.start(model.openPopup(checks), new RerunCmd(checks.onGitHub(), runs));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once GitHub was asked to run the failed jobs again: the commands are logged, a run it refused is shown as an
     * error, and the checks popup, when it still shows that branch, says how it went and reads the checks again at
     * once, as {@code R} does. The branches read their checks again too, when a run was run again.
     */
    private static Next rerun(
            Model model, RerunCmd cmd, List<RerunCmd.Outcome> outcomes, List<ExecutedCommand> commands) {
        Model logged = CommandLog.append(model, RerunCmd.RERUN, commands);
        List<RerunCmd.Outcome> refused =
                outcomes.filter(outcome -> outcome.refused().isDefined());
        Model told = refused.flatMap(outcome -> outcome.refused())
                .headOption()
                .map(failure -> logged.fail(new AppError.OperationFailed(
                        RerunCmd.RERUN,
                        Option.of(refused.map(outcome -> outcome.run().workflow())
                                .distinct()
                                .mkString(", ")),
                        failure)))
                .getOrElse(logged::clearError);
        Next answered = told.popup(ChecksPopup.class)
                .filter(popup -> popup.onGitHub().equals(cmd.branch()))
                .flatMap(popup ->
                        popup.asked().map(asked -> refresh(told.openPopup(popup.withRerun(asked.answered(outcomes))))))
                // Over the checks, they say so once back to them, and read them again by themselves meanwhile.
                .orElse(() -> told.popup(RerunPopup.class)
                        .filter(asking -> asking.checks().onGitHub().equals(cmd.branch()))
                        .flatMap(asking -> asking.checks()
                                .asked()
                                .map(asked -> Next.of(told.openPopup(
                                        asking.over(asking.checks().withRerun(asked.answered(outcomes))))))))
                // Under the menu of the editors to open a log in (ADR 160), likewise.
                .orElse(() -> told.popup(LogEditorPopup.class)
                        .flatMap(asking -> asking.checks()
                                .filter(checks -> checks.onGitHub().equals(cmd.branch()))
                                .flatMap(checks -> checks.asked()
                                        .map(asked -> Next.of(told.openPopup(
                                                asking.over(checks.withRerun(asked.answered(outcomes)))))))))
                .getOrElse(() -> Next.of(told));
        return refused.size() < outcomes.size() ? BranchChecksUpdate.loadAnyway(answered) : answered;
    }

    /**
     * {@code model} reading the checks shown again, as {@code reading} tells, when they can be and {@code due}; else
     * as it is.
     */
    private static Next readAgain(Model model, Predicate<ChecksPopup> due, Reading reading) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.refreshable() && due.test(popup))
                .map(popup -> Next.of(
                        model.openPopup(popup.readingAgain(reading, model.now())), new LoadChecks(popup.onGitHub())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The branch on GitHub the pull request of {@code dialog} merges from, whose checks it shows, once its details are
     * read; none when it has no checks, or comes from a fork, whose branch is not one of this repository, though one
     * here may have its name. Every remote on GitHub is of the same repository, so has the same checks.
     */
    public static Option<RemoteBranch> ofPullRequest(Model model, PullRequestPopup dialog) {
        BranchName head = FromGitHub.branch(dialog.pullRequest().headBranch());
        return model.part(GitHubState.PART)
                .branchChecks()
                .filter(checks -> dialog.detailsRead() && !dialog.fromFork())
                .toList()
                .flatMap(checks -> checks.remotes().toList().sorted())
                .map(remote -> new RemoteBranch(remote, head))
                .find(remote -> model.part(GitHubState.PART).checks(remote).exists(state -> state != Checks.NONE));
    }

    /**
     * The branch on GitHub whose checks {@code branch} shows: itself when remote, else the one it follows; none when
     * that is not on GitHub, or has no checks.
     */
    private static Option<RemoteBranch> onGitHub(Model model, BranchRef branch) {
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
                .filter(remote -> model.part(GitHubState.PART).checks(remote).exists(state -> state != Checks.NONE));
    }

    /**
     * {@code next}, also reading the checks of the branches again when {@code read}, the checks on {@code branch} just
     * read one by one, together do otherwise than the branch shows; unchanged when none were read, or {@code branch} is
     * not on GitHub. The branch takes how they do from that read rather than from {@code read}: the reads of the
     * branch checks run one at a time, so a later one cannot be replaced by an earlier one, while {@code read} may
     * have started before the read the branch shows, and be of a tip since pushed over.
     */
    private static Next caughtUp(Next next, RemoteBranch branch, List<Check> read) {
        return read.isEmpty()
                        || next.model()
                                .part(GitHubState.PART)
                                .checks(branch)
                                .forAll(shown -> shown == Checks.combined(read.map(Check::state)))
                ? next
                : BranchChecksUpdate.loadAnyway(next);
    }

    /**
     * {@code model} showing what was read of the checks on {@code branch}, no less far than they were shown to be
     * (ADR 89); unchanged when they are no longer shown.
     */
    private static Model shown(Model model, RemoteBranch branch, Either<Failure, PendingChecks> read) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.onGitHub().equals(branch))
                .map(popup -> model.openPopup(popup.with(read, model)))
                .orElse(() -> model.popup(RerunPopup.class)
                        .filter(asking -> asking.checks().onGitHub().equals(branch))
                        .map(asking ->
                                model.openPopup(asking.over(asking.checks().with(read, model)))))
                .orElse(() -> model.popup(LogEditorPopup.class)
                        .flatMap(asking -> asking.checks()
                                .filter(checks -> checks.onGitHub().equals(branch))
                                .map(checks -> model.openPopup(asking.over(checks.with(read, model))))))
                .getOrElse(model);
    }
}
