package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Msg.BranchesLoaded;
import net.vanfleteren.jazygit.state.Msg.CommitsLoaded;
import net.vanfleteren.jazygit.state.Msg.LoadFailed;
import net.vanfleteren.jazygit.state.Msg.StatusLoaded;
import net.vanfleteren.jazygit.state.Msg.Tick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next.
 */
public final class Update {

    /**
     * Loads that a tick repeats. They are skipped while a previous one is still running.
     */
    private static final List<Cmd> PERIODIC = List.of(new LoadStatus(), new LoadBranches());

    private Update() {
    }

    /**
     * The next model, and the commands to run for it.
     */
    public record Next(Model model, List<Cmd> cmds) {

        public Next {
            cmds = List.copyOf(cmds);
        }

        static Next of(Model model, Cmd... cmds) {
            return new Next(model, List.of(cmds));
        }
    }

    public static Next init(String repositoryName) {
        // Commits are loaded once the first status tells us where HEAD is.
        return refresh(Model.initial(repositoryName));
    }

    public static Next update(Model model, Msg msg) {
        return switch (msg) {
            case Tick() -> refresh(model);
            case StatusLoaded(RepoStatus status) -> statusLoaded(model, status);
            case BranchesLoaded(var branches) ->
                    Next.of(finished(model, new LoadBranches()).withBranches(model.branches().reload(branches)));
            case CommitsLoaded(var commits) -> Next.of(model.withCommits(model.commits().reload(commits)));
            case LoadFailed(Cmd cmd, String message) -> Next.of(failed(finished(model, cmd), cmd, message));
        };
    }

    private static Next refresh(Model model) {
        List<Cmd> cmds = new ArrayList<>(PERIODIC.stream()
                .filter(cmd -> !model.refreshing().contains(cmd))
                .toList());
        Set<Cmd> refreshing = new HashSet<>(model.refreshing());
        refreshing.addAll(cmds);
        if (model.commits() instanceof Failed) {
            cmds.add(new LoadCommits());
        }
        return new Next(model.withRefreshing(refreshing), cmds);
    }

    private static Next statusLoaded(Model model, RepoStatus status) {
        Model next = finished(model, new LoadStatus()).withStatus(model.status().reload(status));
        boolean headMoved = !(model.status() instanceof Loaded<RepoStatus>(RepoStatus previous)
                && previous.headOid().equals(status.headOid()));
        // Commit loads are not deduplicated: one queued after HEAD moved must still run, even if
        // an earlier one is in progress.
        return headMoved ? Next.of(next, new LoadCommits()) : Next.of(next);
    }

    private static Model finished(Model model, Cmd cmd) {
        return model.withRefreshing(Set.copyOf(model.refreshing().stream()
                .filter(c -> !c.equals(cmd))
                .toList()));
    }

    private static Model failed(Model model, Cmd cmd, String message) {
        return switch (cmd) {
            case LoadStatus() -> model.withStatus(new Failed<>(message));
            case LoadBranches() -> model.withBranches(new Failed<>(message));
            case LoadCommits() -> model.withCommits(new Failed<>(message));
        };
    }
}
