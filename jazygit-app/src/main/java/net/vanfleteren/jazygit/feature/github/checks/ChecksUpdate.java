package net.vanfleteren.jazygit.feature.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.BranchChecksUpdate;
import net.vanfleteren.jazygit.state.github.CheckTimesUpdate;
import net.vanfleteren.jazygit.state.github.FromGitHub;
import net.vanfleteren.jazygit.state.github.PendingChecks;
import net.vanfleteren.jazygit.state.github.Reading;
import net.vanfleteren.jazygit.state.operation.CommandLog;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.state.popup.PopupUpdate;

/**
 * Showing the checks on the tip of a branch one by one, with how long they took or run, and reading them again by
 * hand and by itself, as the pull request dialog does (ADR 19, ADR 32, ADR 34); running the failed ones again
 * (ADR 135).
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
        };
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
        return model.gitHub()
                .branchChecks()
                .filter(checks -> dialog.detailsRead() && !dialog.fromFork())
                .toList()
                .flatMap(checks -> checks.remotes().toList().sorted())
                .map(remote -> new RemoteBranch(remote, head))
                .find(remote -> model.gitHub().checks(remote).exists(state -> state != Checks.NONE));
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
                .filter(remote -> model.gitHub().checks(remote).exists(state -> state != Checks.NONE));
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
                                .gitHub()
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
                .getOrElse(model);
    }
}
