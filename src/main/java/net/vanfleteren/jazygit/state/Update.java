package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.state.Loadable.Loaded;

import net.vanfleteren.jazygit.state.Loadable.Failed;

import net.vanfleteren.jazygit.state.Cmd.LoadStatus;

import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;

import net.vanfleteren.jazygit.state.Cmd.LoadCommits;

import net.vanfleteren.jazygit.state.Cmd.LoadBranches;

import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;

import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return msg.apply(model);
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
        model.commitDetail()
                .filter(detail -> detail.changes() instanceof Failed)
                .ifPresent(detail -> cmds.add(new LoadCommitDetail(detail.sha())));
        return new Next(model.withRefreshing(refreshing), cmds);
    }

    /**
     * Reads the highlighted diff again, as files can change on disk without their status changing. This waits
     * for the status: right after (un)staging, the entries the diff was read for are outdated, and reading
     * them again would show their changes as gone for as long as the new status takes to arrive.
     */
    static Model reloadDiff(Model model, List<Cmd> cmds) {
        if (!(model.status() instanceof Loaded<RepoStatus>(RepoStatus status))) {
            return model;
        }
        return model.fileDiff()
                .filter(d -> status.files().containsAll(d.files()))
                .map(d -> new LoadFileDiff(d.files()))
                .filter(cmd -> !model.refreshing().contains(cmd))
                .map(cmd -> {
                    cmds.add(cmd);
                    return model.withRefreshing(Stream.concat(model.refreshing().stream(), Stream.of(cmd))
                            .collect(Collectors.toUnmodifiableSet()));
                })
                .orElse(model);
    }

    public static List<FileEntry> files(Model model) {
        return model.status() instanceof Loaded<RepoStatus>(RepoStatus status) ? status.files() : List.of();
    }
}
