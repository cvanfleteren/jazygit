package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;

import java.util.function.Function;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next. The transitions of each feature live in their own class, next to their
 * {@link Msg} group.
 */
public final class Update {

    /**
     * Loads that a tick repeats. They are skipped while a previous one is still running.
     */
    private static final List<Cmd.Load> PERIODIC = List.of(new LoadStatus(), new LoadBranches(),
            new Cmd.LoadRemoteBranches(), new Cmd.LoadStashes());

    private Update() {
    }

    /**
     * The next model, and the commands to run for it.
     */
    public record Next(Model model, List<Cmd> cmds) {

        public static Next of(Model model, Cmd... cmds) {
            return new Next(model, List.of(cmds));
        }
    }

    public static Next init(String repositoryName) {
        // Commits are loaded once the first status tells us where HEAD is.
        return refresh(Model.initial(repositoryName));
    }

    public static Next update(Model model, Msg msg) {
        return msg.apply(model);
    }

    /**
     * Starts {@code cmd}: its branch, if it has one, is busy until it is done.
     */
    public static Next start(Model model, OperationCmd cmd) {
        return Next.of(cmd.busyBranch().map(branch -> model.withBusyBranch(branch, cmd.operation())).getOrElse(model), cmd);
    }

    /**
     * Opens {@code popup}, which asks about an operation to start: the error of the last one is not shown
     * anymore.
     */
    public static Next ask(Model model, Popup popup) {
        return Next.of(model.clearError().openPopup(popup));
    }

    /**
     * Opens {@code popup} as {@link #ask(Model, Popup)} does, if there is one: none when the operation it would
     * ask about cannot be done, and then nothing happens.
     */
    public static Next ask(Model model, Option<? extends Popup> popup) {
        return popup.map(open -> ask(model, open)).getOrElse(() -> Next.of(model));
    }

    /**
     * Confirms the open popup of type {@code type}: closes it, clears the error and starts the operation
     * {@code cmd} makes of it. When no such popup is open, or {@code cmd} makes none because what was entered
     * cannot be used, nothing happens.
     */
    public static <P extends Popup> Next confirm(Model model, Class<P> type,
                                                 Function<? super P, Option<? extends OperationCmd>> cmd) {
        return model.popup(type).flatMap(cmd)
                .map(operation -> start(model.withoutPopup(type).clearError(), operation))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Reads the repository again: the periodic loads, and whatever failed to load before. A load that is still
     * running is not started again.
     */
    public static Next refresh(Model model) {
        List<Cmd.Load> retries = List.<Cmd.Load>empty()
                .appendAll(Option.when(model.commits() instanceof Failed, LoadCommits::new))
                .appendAll(model.branchLog()
                        .filter(log -> log.value() instanceof Failed)
                        .map(log -> new LoadBranchLog(log.key())))
                .appendAll(model.commitDetail()
                        .filter(detail -> detail.value() instanceof Failed)
                        .map(detail -> new LoadCommitDetail(detail.key())));
        List<Cmd.Load> loads = PERIODIC.appendAll(retries).filter(cmd -> !model.refreshing().contains(cmd));
        return new Next(model.withRefreshing(model.refreshing().addAll(loads)), List.narrow(loads));
    }

    /**
     * Reads the highlighted diff again, as files can change on disk without their status changing. This waits
     * for the status: right after (un)staging, the entries the diff was read for are outdated, and reading
     * them again would show their changes as gone for as long as the new status takes to arrive.
     */
    static Next reloadDiff(Model model, List<Cmd> cmds) {
        return model.fileDiff()
                .filter(d -> model.status().toOption().exists(status -> status.files().containsAll(d.key())))
                .map(d -> new LoadFileDiff(d.key()))
                .filter(cmd -> !model.refreshing().contains(cmd))
                .map(cmd -> new Next(
                        model.withRefreshing(model.refreshing().add(cmd)),
                        cmds.append(cmd)))
                .getOrElse(() -> new Next(model, cmds));
    }

    /**
     * What {@code then} makes of the changed files, once the status is loaded; until then, or when it failed to
     * load, nothing happens, as it is unknown what changed.
     */
    public static Next withFiles(Model model, Function<List<FileEntry>, Next> then) {
        return model.status().toOption().map(RepoStatus::files).map(then).getOrElse(() -> Next.of(model));
    }

    public static List<FileEntry> files(Model model) {
        return model.status().toOption().map(RepoStatus::files).getOrElse(List.empty());
    }
}
