package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.RemoteBranch;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.FileAt;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.Pipeline;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Showing the builds of a local branch on GitHub (ADR 131).
 */
public sealed interface BuildsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BuildsUpdate.update(model, this);
    }

    /**
     * The user asked for the builds of {@code branch}, or, while they show, for its log again.
     */
    record Toggled(BranchName branch) implements BuildsMsg {}

    /**
     * The user asked, in the builds, for the log of the branch again.
     */
    record Closed() implements BuildsMsg {}

    /**
     * The commits of {@code branch} on GitHub from {@code after} on were read, with their builds; none when it is
     * not on GitHub.
     */
    record Read(RemoteBranch branch, Option<String> after, Option<BuildsPage> page) implements BuildsMsg {}

    record Failed(RemoteBranch branch, Option<String> after, Failure failure) implements BuildsMsg {}

    /**
     * The user asked for the builds of the workflow {@code by} tabs after the one that shows, before it when negative.
     */
    record Stepped(int by) implements BuildsMsg {}

    /**
     * The user asked for the runs of the workflows on every branch in place of the builds of the branch, or back.
     */
    record AllToggled() implements BuildsMsg {}

    /**
     * The user asked for older builds than those read: of more commits of the branch, or, on every branch, more runs
     * of the workflow of the tab (ADR 132).
     */
    record MoreRequested() implements BuildsMsg {}

    /**
     * The user asked to choose how many builds show.
     */
    record WindowRequested() implements BuildsMsg {}

    /**
     * The user chose to show the newest {@code builds} builds; none for all those read.
     */
    record WindowPicked(Option<Integer> builds) implements BuildsMsg {}

    /**
     * The user asked to pick the build whose jobs show on one time line (ADR 133).
     */
    record BuildPickRequested() implements BuildsMsg {}

    /**
     * The user picked the build of the run {@code run}; none for the last build that is done (ADR 133).
     */
    record BuildPicked(Option<Long> run) implements BuildsMsg {}

    /**
     * The user asked for the build {@code by} rows after the one on the time line, an older one when positive, to
     * show its jobs there (ADR 133).
     */
    record BuildStepped(int by) implements BuildsMsg {}

    /**
     * The user asked to choose the workflow whose builds show from all of them.
     */
    record PickRequested() implements BuildsMsg {}

    /**
     * The user chose {@code pipeline} in the popup.
     */
    record Picked(Pipeline pipeline) implements BuildsMsg {}

    /**
     * The user clicked the tab of {@code pipeline}.
     */
    record TabClicked(Pipeline pipeline) implements BuildsMsg {}

    /**
     * The user clicked the build of the run {@code run}, in the chart or the list: its jobs show on one time line, or
     * those of the last build again when it already did (ADR 133).
     */
    record BuildClicked(long run) implements BuildsMsg {}

    /**
     * The files of workflows were read at the commit of a build, each as its workflow, or none when it could not be.
     */
    record WorkflowsRead(Map<FileAt, Option<Workflow>> workflows) implements BuildsMsg {}

    /**
     * Runs of the workflow {@code workflowId} on every branch were read from {@code after} on; none when it is no
     * workflow.
     */
    record RunsRead(String workflowId, Option<String> after, Option<WorkflowRunsPage> page) implements BuildsMsg {}

    record RunsFailed(String workflowId, Option<String> after, Failure failure) implements BuildsMsg {}

    /**
     * The workflows of the repository were read.
     */
    record RepoWorkflowsRead(List<RepoWorkflow> workflows) implements BuildsMsg {}

    record RepoWorkflowsFailed(Failure failure) implements BuildsMsg {}
}
