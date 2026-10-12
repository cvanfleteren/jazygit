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
    private static final List<Cmd.Load> PERIODIC = List.of(new LoadStatus(), new LoadBranches(), new Cmd.LoadStashes());

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
        return Next.of(cmd.busyBranch().map(model::withBusyBranch).getOrElse(model), cmd);
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

    public static Next refresh(Model model) {
        List<Cmd.Load> loads = PERIODIC.filter(cmd -> !model.refreshing().contains(cmd));
        List<Cmd> cmds = List.<Cmd>empty()
                .appendAll(loads)
                .appendAll(model.commits() instanceof Failed ? List.of(new LoadCommits()) : List.empty())
                .appendAll(model.branchLog()
                        .filter(log -> log.commits() instanceof Failed)
                        .map(log -> new LoadBranchLog(log.branch()))
                        .map(List::<Cmd>of).getOrElse(List.empty()))
                .appendAll(model.commitDetail()
                        .filter(detail -> detail.changes() instanceof Failed)
                        .map(detail -> new LoadCommitDetail(detail.sha()))
                        .map(List::<Cmd>of).getOrElse(List.empty()));
        return new Next(model.withRefreshing(model.refreshing().addAll(loads)), cmds);
    }

    /**
     * Reads the highlighted diff again, as files can change on disk without their status changing. This waits
     * for the status: right after (un)staging, the entries the diff was read for are outdated, and reading
     * them again would show their changes as gone for as long as the new status takes to arrive.
     */
    static Next reloadDiff(Model model, List<Cmd> cmds) {
        return model.fileDiff()
                .filter(d -> model.status().toOption().exists(status -> status.files().containsAll(d.files())))
                .map(d -> new LoadFileDiff(d.files()))
                .filter(cmd -> !model.refreshing().contains(cmd))
                .map(cmd -> new Next(
                        model.withRefreshing(model.refreshing().add(cmd)),
                        cmds.append(cmd)))
                .getOrElse(() -> new Next(model, cmds));
    }

    public static List<FileEntry> files(Model model) {
        return model.status().toOption().map(RepoStatus::files).getOrElse(List.empty());
    }
}
