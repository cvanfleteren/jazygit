package net.vanfleteren.jazygit.state.github.builds;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import net.vanfleteren.jazygit.state.view.Workflow;

/**
 * What the builds of a branch read besides its builds, and a click on a tab of them (ADR 131).
 */
public sealed interface BuildsViewMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BuildsViewUpdate.update(model, this);
    }

    /**
     * The user clicked the tab of {@code pipeline}.
     */
    record TabClicked(Pipeline pipeline) implements BuildsViewMsg {}

    /**
     * The user clicked the build of the run {@code run}, in the chart or the list: its jobs show on one time line, or
     * those of the last build again when it already did (ADR 133).
     */
    record BuildClicked(long run) implements BuildsViewMsg {}

    /**
     * The files of workflows were read at the commit of a build, each as its workflow, or none when it could not be.
     */
    record WorkflowsRead(Map<FileAt, Option<Workflow>> workflows) implements BuildsViewMsg {}

    /**
     * Runs of the workflow {@code workflowId} on every branch were read from {@code after} on; none when it is no
     * workflow.
     */
    record RunsRead(String workflowId, Option<String> after, Option<WorkflowRunsPage> page) implements BuildsViewMsg {}

    record RunsFailed(String workflowId, Option<String> after, Failure failure) implements BuildsViewMsg {}

    /**
     * The workflows of the repository were read.
     */
    record RepoWorkflowsRead(List<RepoWorkflow> workflows) implements BuildsViewMsg {}

    record RepoWorkflowsFailed(Failure failure) implements BuildsViewMsg {}
}
