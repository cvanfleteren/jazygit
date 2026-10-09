package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.Checkout;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Msg.BranchLogLoaded;
import net.vanfleteren.jazygit.state.Msg.BranchSelected;
import net.vanfleteren.jazygit.state.Msg.BranchesLoaded;
import net.vanfleteren.jazygit.state.Msg.CheckedOut;
import net.vanfleteren.jazygit.state.Msg.CheckoutFailed;
import net.vanfleteren.jazygit.state.Msg.CheckoutRequested;
import net.vanfleteren.jazygit.state.Msg.CommitsLoaded;
import net.vanfleteren.jazygit.state.Msg.LoadFailed;
import net.vanfleteren.jazygit.state.Msg.StatusLoaded;
import net.vanfleteren.jazygit.state.Msg.Tick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next.
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
            case BranchesLoaded(var branches) -> branchesLoaded(model, branches);
            case CommitsLoaded(var commits) -> Next.of(model.withCommits(model.commits().reload(commits)));
            case BranchSelected(String branch) -> branchSelected(model, branch);
            // A log that arrives after another branch was highlighted is dropped.
            case BranchLogLoaded(String branch, var commits) ->
                    Next.of(updateBranchLog(model, branch, log -> log.reload(commits)));
            case LoadFailed(Cmd.Load cmd, String message) -> Next.of(failed(finished(model, cmd), cmd, message));
            case CheckoutRequested(String branch) -> checkoutRequested(model, branch);
            // Status and branches show the new HEAD; the moved HEAD then reloads the commits.
            case CheckedOut(String branch) -> refresh(model.withError(Optional.empty()));
            case CheckoutFailed(String branch, String message) ->
                    Next.of(model.withError(Optional.of("Checkout of " + branch + " failed: " + message)));
        };
    }

    private static Next refresh(Model model) {
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

    private static Next branchesLoaded(Model model, List<Branch> branches) {
        Model next = finished(model, new LoadBranches()).withBranches(model.branches().reload(branches));
        List<Branch> previous = model.branches() instanceof Loaded<List<Branch>>(List<Branch> value)
                ? value
                : List.of();
        // The highlighted branch moved (a commit, reset, fetch...): its log is outdated.
        return model.branchLog()
                .map(BranchLog::branch)
                .filter(branch -> !tip(previous, branch).equals(tip(branches, branch)))
                .map(branch -> Next.of(next, new LoadBranchLog(branch)))
                .orElseGet(() -> Next.of(next));
    }

    private static Optional<String> tip(List<Branch> branches, String name) {
        return branches.stream()
                .filter(b -> b.name().equals(name))
                .map(Branch::tipOid)
                .findFirst();
    }

    private static Next branchSelected(Model model, String branch) {
        if (model.branchLog().map(BranchLog::branch).filter(branch::equals).isPresent()) {
            return Next.of(model);
        }
        return Next.of(model.withBranchLog(Optional.of(BranchLog.loading(branch, model.branchLog()))),
                new LoadBranchLog(branch));
    }

    /**
     * Applies {@code f} to the branch log if it is the log of {@code branch}.
     */
    private static Model updateBranchLog(Model model, String branch, UnaryOperator<BranchLog> f) {
        return model.withBranchLog(model.branchLog().map(log -> log.branch().equals(branch) ? f.apply(log) : log));
    }

    private static Next checkoutRequested(Model model, String branch) {
        boolean known = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch) && !b.current());
        return known ? Next.of(model.withError(Optional.empty()), new Checkout(branch)) : Next.of(model);
    }

    private static Model finished(Model model, Cmd.Load cmd) {
        return model.withRefreshing(Set.copyOf(model.refreshing().stream()
                .filter(c -> !c.equals(cmd))
                .toList()));
    }

    private static Model failed(Model model, Cmd.Load cmd, String message) {
        return switch (cmd) {
            case LoadStatus() -> model.withStatus(new Failed<>(message));
            case LoadBranches() -> model.withBranches(new Failed<>(message));
            case LoadCommits() -> model.withCommits(new Failed<>(message));
            case LoadBranchLog(String branch) ->
                    updateBranchLog(model, branch, log -> new BranchLog(branch, new Failed<>(message)));
        };
    }
}
