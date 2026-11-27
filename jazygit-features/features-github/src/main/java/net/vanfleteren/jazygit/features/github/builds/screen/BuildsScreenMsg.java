package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.DefaultBranchPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.builds.FileAt;
import net.vanfleteren.jazygit.features.github.builds.Workflow;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * The builds screen (ADR 212): what the user does on it, and what was read for it.
 */
public sealed interface BuildsScreenMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BuildsScreenUpdate.update(model, this);
    }

    /**
     * A minute went by: the screen reads again what it shows, while it is shown.
     */
    record Tick() implements BuildsScreenMsg {}

    /**
     * A while went by: the screen reads the newest runs it shows again, while one of them runs.
     */
    record RunningTick() implements BuildsScreenMsg {}

    /**
     * The user asked for the builds screen from the local branch {@code branch}: its runs show (ADR 212).
     */
    record Opened(BranchName branch) implements BuildsScreenMsg {}

    /**
     * The user highlighted {@code workflow} in the workflows pane.
     */
    record WorkflowHighlighted(String workflow) implements BuildsScreenMsg {}

    /**
     * The user highlighted the run {@code run} in the runs pane.
     */
    record RunHighlighted(long run) implements BuildsScreenMsg {}

    /**
     * The user clicked the run {@code run} in the chart: the runs pane highlights it.
     */
    record RunClicked(long run) implements BuildsScreenMsg {}

    /**
     * The user asked to choose which runs show.
     */
    record FilterRequested() implements BuildsScreenMsg {}

    /**
     * The user chose to show {@code filter}'s runs.
     */
    record FilterPicked(BuildsScreen.Filter filter) implements BuildsScreenMsg {}

    /**
     * The user asked to choose how many runs the chart shows.
     */
    record WindowRequested() implements BuildsScreenMsg {}

    /**
     * The user chose to show the newest {@code runs} runs in the chart; none for as many as fit.
     */
    record WindowPicked(Option<Integer> runs) implements BuildsScreenMsg {}

    /**
     * The user asked for older runs than those read, of whatever the filter shows.
     */
    record MoreRequested() implements BuildsScreenMsg {}

    /**
     * The user asked to open {@code run} in the browser.
     */
    record BrowseRequested(Build run) implements BuildsScreenMsg {}

    /**
     * The user asked to run the failed jobs of {@code run} again.
     */
    record RerunRequested(Build run) implements BuildsScreenMsg {}

    /**
     * The workflows of the repository were read.
     */
    record WorkflowsRead(List<RepoWorkflow> workflows) implements BuildsScreenMsg {}

    record WorkflowsFailed(Failure failure) implements BuildsScreenMsg {}

    /**
     * The commits of the default branch from {@code after} on were read, with their builds, when {@code refresh} the
     * newest again; none in a repository without a commit.
     */
    record DefaultRead(Option<String> after, boolean refresh, Option<DefaultBranchPage> page)
            implements BuildsScreenMsg {}

    record DefaultFailed(Option<String> after, boolean refresh, Failure failure) implements BuildsScreenMsg {}

    /**
     * The commits of {@code branch} from {@code after} on were read, with their builds; none when it is not on GitHub.
     */
    record BranchRead(RemoteBranch branch, Option<String> after, boolean refresh, Option<BuildsPage> page)
            implements BuildsScreenMsg {}

    record BranchFailed(RemoteBranch branch, Option<String> after, boolean refresh, Failure failure)
            implements BuildsScreenMsg {}

    /**
     * Runs of the workflow {@code workflowId} on every branch were read from {@code after} on; none when it is no
     * workflow.
     */
    record RunsRead(String workflowId, Option<String> after, boolean refresh, Option<WorkflowRunsPage> page)
            implements BuildsScreenMsg {}

    record RunsFailed(String workflowId, Option<String> after, boolean refresh, Failure failure)
            implements BuildsScreenMsg {}

    /**
     * {@code runs} runs are in progress on any branch.
     */
    record InProgressRead(int runs) implements BuildsScreenMsg {}

    record InProgressFailed(Failure failure) implements BuildsScreenMsg {}

    /**
     * The files of workflows were read at the commit of a run, each as its workflow, or none when it could not be.
     */
    record FilesRead(Map<FileAt, Option<Workflow>> files) implements BuildsScreenMsg {}
}
