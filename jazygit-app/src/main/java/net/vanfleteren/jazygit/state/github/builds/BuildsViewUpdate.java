package net.vanfleteren.jazygit.state.github.builds;

import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.WorkflowRuns;

/**
 * What the builds of a branch read besides its builds, and a click on a tab of them; each reads what it then needs
 * (ADR 131).
 */
public final class BuildsViewUpdate {

    private BuildsViewUpdate() {}

    public static Next update(Model model, BuildsViewMsg msg) {
        return switch (msg) {
            case BuildsViewMsg.TabClicked(var pipeline) ->
                BuildsReads.next(changed(model, view -> view.withSelected(pipeline)));
            case BuildsViewMsg.WorkflowsRead(var workflows) ->
                Next.of(changed(model, view -> view.withWorkflows(workflows)));
            case BuildsViewMsg.RunsRead(String id, var after, var page) ->
                awaited(model, id, after, runs -> runs.read(page));
            case BuildsViewMsg.RunsFailed(String id, var after, var failure) ->
                awaited(model, id, after, runs -> runs.failed(failure));
            case BuildsViewMsg.RepoWorkflowsRead(var workflows) ->
                BuildsReads.next(
                        changed(model, view -> view.withRepoWorkflows(Option.of(new Loadable.Loaded<>(workflows)))));
            case BuildsViewMsg.RepoWorkflowsFailed(var failure) ->
                Next.of(changed(model, view -> view.withRepoWorkflows(Option.of(new Loadable.Failed<>(failure)))));
        };
    }

    /**
     * {@code model} with what {@code read} makes of the runs of the workflow {@code id} from {@code after} on, when
     * those are still awaited; reading on from where they end while more are to be read.
     */
    private static Next awaited(Model model, String id, Option<String> after, UnaryOperator<WorkflowRuns> read) {
        return model.repoView()
                .builds()
                .flatMap(view -> view.runs()
                        .get(id)
                        .filter(runs -> runs.awaits(after))
                        .map(runs -> {
                            WorkflowRuns now = read.apply(runs);
                            Model shown = changed(
                                    model, ignored -> view.withRuns(view.runs().put(id, now)));
                            return now.reading()
                                    ? BuildsReads.next(shown).and(new LoadWorkflowRuns(id, now.after()))
                                    : BuildsReads.next(shown);
                        }))
                .getOrElse(() -> Next.of(model));
    }

    private static Model changed(Model model, UnaryOperator<BuildsView> change) {
        return model.withRepoView(
                model.repoView().withBuilds(model.repoView().builds().map(change)));
    }
}
