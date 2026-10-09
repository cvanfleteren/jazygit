package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.state.update.*;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next. The transitions of each feature live in their own class, next to their
 * {@link Msg} group.
 */
public final class Update {

    /**
     * Loads that a tick repeats. They are skipped while a previous one is still running.
     */
    private static final List<Cmd.Load> PERIODIC = List.of(new LoadStatus(), new LoadBranches());

    private Update() {
    }

    /**
     * The next model, and the commands to run for it.
     */
    public record Next(Model model, List<Cmd> cmds) {

        public Next {
            cmds = List.copyOf(cmds);
        }

        public static Next of(Model model, Cmd... cmds) {
            return new Next(model, List.of(cmds));
        }
    }

    public static Next init(String repositoryName) {
        // Commits are loaded once the first status tells us where HEAD is.
        return refresh(Model.initial(repositoryName));
    }

    public static Next update(Model model, Msg msg) {
        return switch (msg) {
            case Msg.Tick() -> refresh(model);
            case LoadMsg m -> LoadUpdate.update(model, m);
            case SelectionMsg m -> SelectionUpdate.update(model, m);
            case CheckoutMsg m -> CheckoutUpdate.update(model, m);
            case NewBranchMsg m -> NewBranchUpdate.update(model, m);
            case DeleteBranchMsg m -> DeleteBranchUpdate.update(model, m);
            case StageMsg m -> StageUpdate.update(model, m);
            case CommitMsg m -> CommitUpdate.update(model, m);
            case AmendMsg m -> AmendUpdate.update(model, m);
            case RewordMsg m -> RewordUpdate.update(model, m);
        };
    }

    public static Next refresh(Model model) {
        List<Cmd.Load> loads = PERIODIC.stream()
                .filter(cmd -> !model.refreshing().contains(cmd))
                .toList();
        Set<Cmd.Load> refreshing = new HashSet<>(model.refreshing());
        refreshing.addAll(loads);
        List<Cmd> cmds = new ArrayList<>(loads);
        if (model.commits() instanceof Failed) {
            cmds.add(new LoadCommits());
        }
        model.branchLog()
                .filter(log -> log.commits() instanceof Failed)
                .ifPresent(log -> cmds.add(new LoadBranchLog(log.branch())));
        // The files can change on disk without their status changing, so the diff is read again.
        model.fileDiff()
                .map(d -> new LoadFileDiff(d.files()))
                .filter(cmd -> !refreshing.contains(cmd))
                .ifPresent(cmd -> {
                    refreshing.add(cmd);
                    cmds.add(cmd);
                });
        return new Next(model.withRefreshing(refreshing), cmds);
    }

    public static List<FileEntry> files(Model model) {
        return model.status() instanceof Loaded<RepoStatus>(RepoStatus status) ? status.files() : List.of();
    }
}
