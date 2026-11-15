package net.vanfleteren.jazygit.state.github.builds;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
import net.vanfleteren.jazygit.state.view.Workflow;
import net.vanfleteren.jazygit.state.view.WorkflowRuns;

/**
 * What the builds of a branch read besides its builds, once they show what needs it (ADR 131): the files of the
 * workflows of their last builds that are done, the runs on every branch of the workflow of the tab shown, and on
 * every branch, the workflows of the repository. Each is read once a view.
 */
public final class BuildsReads {

    private BuildsReads() {}

    /**
     * Of the builds of {@code view}, those that show: with only the commits not on the base listed ({@code b}), those
     * of the commits not on it, as the log of the branch (ADR 20, 131).
     */
    public static List<Build> shownBuilds(Model model, BuildsView view) {
        return BuildsView.shown(
                view.builds().getOrElse(List.empty()),
                AheadUpdate.branch(model, new BranchRef.Local(view.branch())),
                model.repoView().commits().shown().ahead());
    }

    /**
     * {@code model}, reading what its builds show and is not read yet, nor being read.
     */
    public static Next next(Model model) {
        return model.repoView()
                .branches()
                .builds()
                .filter(view -> view.builds().toOption().isDefined())
                .map(view -> {
                    List<FileAt> files = view.unread();
                    boolean repo = view.allBranches() && view.repoWorkflows().isEmpty();
                    Option<String> runs = view.shownTab(shownBuilds(model, view))
                            .flatMap(tab -> view.workflowId(tab.workflow()))
                            .filter(id -> !view.runs().containsKey(id));
                    BuildsView asked = view.withWorkflows(
                                    HashMap.ofEntries(files.map(file -> Tuple.of(file, Option.<Workflow>none()))))
                            .withRepoWorkflows(repo ? Option.of(Loadable.loading()) : view.repoWorkflows())
                            .withRuns(runs.map(id -> view.runs().put(id, WorkflowRuns.asked()))
                                    .getOrElse(view.runs()));
                    List<Cmd> cmds = files.<Cmd>map(ReadWorkflows::new)
                            .appendAll(Option.when(repo, LoadRepoWorkflows::new))
                            .appendAll(runs.map(id -> new LoadWorkflowRuns(id, Option.none())));
                    return new Next(
                            model.withRepoView(model.repoView()
                                    .withBranches(model.repoView().branches().withBuilds(Option.of(asked)))),
                            cmds,
                            false);
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code next}, then reading what its model's builds show and is not read yet.
     */
    public static Next after(Next next) {
        Next more = next(next.model());
        return new Next(more.model(), next.cmds().appendAll(more.cmds()), next.quit());
    }
}
