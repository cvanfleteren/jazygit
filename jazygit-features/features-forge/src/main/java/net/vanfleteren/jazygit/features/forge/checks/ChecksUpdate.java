package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.edit.EditedFile;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.CheckList;
import net.vanfleteren.jazygit.kernel.state.popup.Covering;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Showing the checks on the tip of a branch on a forge one by one, and reading them again by hand and by itself (ADR
 * 32, 34, 209); running the failed ones again (ADR 135); picking one, and opening its log in an editor (ADR 160). What
 * is the forge's own, it does through the {@link ChecksPort} the popup carries.
 */
public final class ChecksUpdate {

    private ChecksUpdate() {}

    public static Next update(Model model, ChecksMsg msg) {
        return switch (msg) {
            case ChecksMsg.Requested(ChecksPort port, var branch) -> opened(model, port, port.ofBranch(model, branch));
            // Once its details are read; Escape goes back to it.
            case ChecksMsg.PullRequestChecksRequested(ChecksPort port) ->
                opened(model, port, port.ofPullRequest(model));
            case ChecksMsg.Closed() ->
                Next.of(model.popup(ChecksPopup.class)
                        .flatMap(ChecksPopup::from)
                        .map(model::openPopup)
                        .getOrElse(() -> model.withoutPopup(ChecksPopup.class)));
            // What else the forge does with the checks read, it does whether they are still shown or not.
            case ChecksMsg.Read(ChecksPort port, RemoteBranch branch, Either<Failure, ChecksRead> read) -> {
                Next shown = Next.of(shown(model, branch, popup -> popup.showing(read, model)));
                yield read.fold(failure -> shown, checks -> port.loaded(shown, branch, checks));
            }
            case ChecksMsg.RefreshChosen() -> refresh(model);
            case ChecksMsg.Tick(ChecksPort port) ->
                readAgain(
                        model, popup -> popup.port().equals(port) && popup.dueByItself(model.now()), Reading.BY_ITSELF);
            case ChecksMsg.RerunRequested() -> rerunRequested(model);
            case ChecksMsg.RerunCancelled() ->
                Next.of(model.popup(RerunPopup.class)
                        .map(asking -> model.openPopup(asking.checks()))
                        .getOrElse(model));
            case ChecksMsg.RerunConfirmed() -> rerunConfirmed(model);
            case ChecksMsg.Rerun(ChecksPort port, RemoteBranch branch, OperationCmd cmd, var outcomes, var commands) ->
                rerun(model, port, branch, cmd, outcomes, commands);
            case ChecksMsg.Stepped(int by) -> Next.of(stepped(model, by));
            case ChecksMsg.Clicked(CheckPick pick) ->
                Next.of(model.popup(ChecksPopup.class)
                        .flatMap(popup -> popup.checksRead()
                                .find(pick::matches)
                                .map(check -> model.openPopup(popup.picking(check))))
                        .getOrElse(model));
            case ChecksMsg.LogRequested() -> logRequested(model);
            case ChecksMsg.LogRead(OperationCmd cmd, String name, String file, int line, var commands) ->
                logRead(model, cmd, name, file, line, commands);
        };
    }

    /**
     * The checks popup of {@code port} where {@code opening} says, while its checks are first read; as it is without
     * one.
     */
    private static Next opened(Model model, ChecksPort port, Option<ChecksPort.Opening> opening) {
        return opening.map(where -> {
                    ChecksPopup popup = ChecksPopup.loading(port, where, model.now());
                    return Next.of(PopupUpdate.ask(model, popup).model(), port.load(model, popup));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The check {@code by} rows after the one picked picked, in the order they are listed, as far as there are; the
     * checks scrolled as little as it takes to show it (ADR 160).
     */
    private static Model stepped(Model model, int by) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> !popup.checksRead().isEmpty())
                .map(popup -> {
                    List<ForgeCheck> ordered = popup.ordered(model);
                    int at = popup.pickedCheck(model).map(ordered::indexOf).getOrElse(0);
                    int to = Math.clamp((long) at + by, 0, ordered.size() - 1);
                    return model.openPopup(popup.picking(ordered.get(to)))
                            .withListCursor(CheckList.scrolledTo(model.listCursor(), to, ordered.size()));
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
                                        model.openPopup(popup.picking(check).withNoLogAsked(true))))
                                .orElse(() -> popup.port()
                                        .log(popup, check)
                                        .map(cmd ->
                                                OperationUpdate.start(model.openPopup(popup.picking(check)), cmd)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once the log of the check {@code name} was read: the commands are logged, and it opens as a file does, by the
     * feature that opens files (ADR 145, 206): the menu of the editors, if it asks, goes over the checks and back to
     * them.
     */
    private static Next logRead(
            Model model, OperationCmd cmd, String name, String file, int line, List<ExecutedCommand> commands) {
        Model done = OperationUpdate.done(model, cmd, commands).clearError();
        return Events.happened(
                Next.of(done),
                KernelEvent.FileToOpen.at(EditedFile.onDisk(file), line)
                        .titled(Phrase.of("forge.dialog.logEditor.title", name)));
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
                            ? model.openPopup(popup.noting(new RerunNote.Nothing(plan.nothing())))
                            : model.openPopup(new RerunPopup(popup, plan));
                })
                .getOrElse(model));
    }

    /**
     * Back to the checks, which say the failed ones were asked to run again, while the forge is asked to.
     */
    private static Next rerunConfirmed(Model model) {
        return model.popup(RerunPopup.class)
                .map(asking -> {
                    List<RerunPlan.Run> runs = asking.plan().runs();
                    ChecksPopup checks =
                            asking.checks().noting(RerunNote.Asked.at(model.now(), runs.map(RerunPlan.Run::run)));
                    return OperationUpdate.start(
                            model.openPopup(checks), checks.port().rerun(checks, runs));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once the forge {@code port} was asked by {@code cmd} to run the failed jobs on {@code branch} again: the commands are logged,
     * a run it refused is shown as an error, and the checks popup, when it still shows that branch, says how it went
     * and reads the checks again at once, as {@code R} does. When a run was run again, the forge reads again what that
     * changed.
     */
    private static Next rerun(
            Model model,
            ChecksPort port,
            RemoteBranch branch,
            OperationCmd cmd,
            List<RerunPlan.Outcome> outcomes,
            List<ExecutedCommand> commands) {
        Model logged = OperationUpdate.done(model, cmd, commands);
        List<RerunPlan.Outcome> refused =
                outcomes.filter(outcome -> outcome.refused().isDefined());
        Model told = refused.flatMap(RerunPlan.Outcome::refused)
                .headOption()
                .map(failure -> logged.fail(new AppError.OperationFailed(
                        cmd.operation(),
                        Option.of(refused.map(outcome -> outcome.run().workflow())
                                .distinct()
                                .mkString(", ")),
                        failure)))
                .getOrElse(logged::clearError);
        UnaryOperator<ChecksPopup> answered = popup -> popup.asked()
                .map(asked -> popup.noting(asked.answered(outcomes)))
                .getOrElse(popup);
        Next shown = told.popup(ChecksPopup.class)
                .filter(popup -> popup.onForge().equals(branch))
                .filter(popup -> popup.asked().isDefined())
                .map(popup -> refresh(told.openPopup(answered.apply(popup))))
                // Over the checks, they say so once back to them, and read them again by themselves meanwhile.
                .getOrElse(() -> Next.of(shown(told, branch, answered)));
        return refused.size() < outcomes.size() ? port.reran(shown) : shown;
    }

    /**
     * {@code model} reading the checks shown again, as {@code reading} tells, when they can be and {@code due}; else
     * as it is.
     */
    private static Next readAgain(Model model, Predicate<ChecksPopup> due, Reading reading) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.refreshable() && due.test(popup))
                .map(popup -> {
                    ChecksPopup again = popup.readingAgain(reading, model.now());
                    return Next.of(model.openPopup(again), again.port().load(model, again));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code model} with the checks popup of {@code branch} changed as {@code change} says: the one open, or the one
     * under the menu to run failed checks again or to open a log in (ADR 160); unchanged when none shows them.
     */
    private static Model shown(Model model, RemoteBranch branch, UnaryOperator<ChecksPopup> change) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.onForge().equals(branch))
                .map(popup -> model.openPopup(change.apply(popup)))
                .orElse(() -> model.popup(RerunPopup.class)
                        .filter(asking -> asking.checks().onForge().equals(branch))
                        .map(asking -> model.openPopup(asking.over(change.apply(asking.checks())))))
                .orElse(() -> model.popup(Covering.class)
                        .flatMap(asking -> asking.under()
                                .filter(ChecksPopup.class::isInstance)
                                .map(ChecksPopup.class::cast)
                                .filter(checks -> checks.onForge().equals(branch))
                                .map(checks -> model.openPopup(asking.over(change.apply(checks))))))
                .getOrElse(model);
    }
}
