package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.FileAt;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What the builds of a branch read besides its builds, once they show what needs it (ADR 131): the files of the
 * workflows of their last builds that are done, the runs on every branch of the workflow of the tab shown, and on
 * every branch, the workflows of the repository. Each is read once a view.
 */
public final class BuildsReads {

    private BuildsReads() {}

    /**
     * {@code model}, reading what its builds show and is not read yet, nor being read.
     */
    public static Next next(Model model) {
        return model.part(BuildsView.PART)
                .filter(view -> view.builds().toOption().isDefined())
                .map(view -> {
                    List<FileAt> files = view.unread();
                    boolean repo = view.allBranches() && view.repoWorkflows().isEmpty();
                    Option<String> runs = view.shownTab()
                            .flatMap(tab -> view.workflowId(tab.workflow()))
                            .filter(id -> !view.runs().containsKey(id));
                    BuildsView asked = view.withWorkflows(
                                    HashMap.ofEntries(files.map(file -> Tuple.of(file, Option.<Workflow>none()))))
                            .withRepoWorkflows(repo ? Option.of(Loadable.loading()) : view.repoWorkflows())
                            .withRuns(runs.map(id -> view.runs().put(id, BuildPages.asked()))
                                    .getOrElse(view.runs()));
                    List<Cmd> cmds = files.<Cmd>map(ReadWorkflows::new)
                            .appendAll(Option.when(repo, LoadRepoWorkflows::new))
                            .appendAll(runs.map(id -> new LoadWorkflowRuns(id, Option.none())));
                    return Next.of(model.withPart(BuildsView.PART, Option.of(asked)))
                            .and(cmds);
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code next}, then reading what its model's builds show and is not read yet.
     */
    public static Next after(Next next) {
        return next.then(BuildsReads::next);
    }
}
