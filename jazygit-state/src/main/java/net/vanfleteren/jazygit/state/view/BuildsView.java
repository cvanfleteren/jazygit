package net.vanfleteren.jazygit.state.view;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.Comparator;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * The builds of a local branch on GitHub, which the content panel shows in place of its log (ADR 131).
 *
 * @param branch        the local branch
 * @param onGitHub      the branch on GitHub it follows, whose builds are read; none when it follows none
 * @param builds        the builds read so far, of the newest commit first
 * @param commits       how many commits of the branch were read, with or without builds
 * @param limit         how many commits are read before it stops, until more are asked for
 * @param after         where the commits still to be read start; none at the tip
 * @param reading       whether more commits are being read
 * @param stopped       why reading more failed, after some were read; none while it did not
 * @param workflows     the files of the workflows read, at the commit of a build, which say which job needs which;
 *                      none for one being read, or that could not be
 * @param selected      the workflow whose builds show, as the user chose it; none to show the one on the tip
 * @param allBranches   whether a tab shows the runs of its workflow on every branch, not only the branch's builds
 * @param runs          the runs of workflows on every branch, by the id of the workflow, as far as they are read
 * @param repoWorkflows the workflows of the repository, once they are asked for: the tabs of every branch
 * @param window        how many of the builds of a tab show, the newest; none for all those read
 * @param picked        the run of the build whose jobs show on one time line, as the user picked it; none for the
 *                      last build that is done (ADR 133)
 */
public record BuildsView(
        BranchName branch,
        Option<RemoteBranch> onGitHub,
        Loadable<List<Build>> builds,
        int commits,
        int limit,
        Option<String> after,
        boolean reading,
        Option<Failure> stopped,
        Map<BuildsView.FileAt, Option<Workflow>> workflows,
        Option<BuildsView.Pipeline> selected,
        @With boolean allBranches,
        @With Map<String, WorkflowRuns> runs,
        @With Option<Loadable<List<RepoWorkflow>>> repoWorkflows,
        @With Option<Integer> window,
        @With Option<Long> picked) {

    /**
     * How many commits are read at first, and how many more each time more are asked for (ADR 131, 132).
     */
    public static final int COMMITS = 100;

    /**
     * The builds of {@code branch}, which follows {@code onGitHub}, before any is read.
     */
    public static BuildsView reading(BranchName branch, RemoteBranch onGitHub) {
        return new BuildsView(
                branch,
                Option.of(onGitHub),
                Loadable.loading(),
                0,
                COMMITS,
                Option.none(),
                true,
                Option.none(),
                HashMap.empty(),
                Option.none(),
                false,
                HashMap.empty(),
                Option.none(),
                Option.none(),
                Option.none());
    }

    /**
     * The builds of {@code branch}, which follows no branch on GitHub, so has none.
     */
    public static BuildsView offGitHub(BranchName branch) {
        return new BuildsView(
                branch,
                Option.none(),
                new Loadable.Loaded<>(List.empty()),
                0,
                COMMITS,
                Option.none(),
                false,
                Option.none(),
                HashMap.empty(),
                Option.none(),
                false,
                HashMap.empty(),
                Option.none(),
                Option.none(),
                Option.none());
    }

    /**
     * The last of {@code builds}, the newest first, that is done and ran a job: the one whose jobs show on one time
     * line.
     */
    public static Option<Build> lastDoneOf(List<Build> builds) {
        return builds.find(build -> build.duration().isDefined() && !build.ran().isEmpty());
    }

    /**
     * The middle of {@code durations}; none without any.
     */
    public static Option<Duration> median(List<Duration> durations) {
        List<Duration> sorted = durations.sorted();
        int size = sorted.size();
        if (size == 0) {
            return Option.none();
        }
        return Option.of(
                size % 2 == 1
                        ? sorted.get(size / 2)
                        : sorted.get(size / 2 - 1).plus(sorted.get(size / 2)).dividedBy(2));
    }

    /**
     * Whether this waits for the commits of {@code branch} from {@code from} on.
     */
    public boolean awaits(RemoteBranch branch, Option<String> from) {
        return reading && onGitHub.contains(branch) && after.equals(from);
    }

    /**
     * This, with the builds of {@code page} after those read; it reads on while fewer than {@code limit} commits
     * were read and older ones are left. None read means the branch is not on GitHub.
     */
    public BuildsView read(Option<BuildsPage> page) {
        int read = commits + page.map(BuildsPage::commits).getOrElse(0);
        Option<String> next = page.flatMap(BuildsPage::next);
        return new BuildsView(
                branch,
                onGitHub,
                new Loadable.Loaded<>(builds.getOrElse(List.empty())
                        .appendAll(page.map(BuildsPage::builds).getOrElse(List.empty()))),
                read,
                limit,
                next,
                read < limit && next.isDefined(),
                Option.none(),
                workflows,
                selected,
                allBranches,
                runs,
                repoWorkflows,
                window,
                picked);
    }

    /**
     * This, done reading as it failed for {@code failure}: what was read stays, stopped for it; with nothing read yet,
     * the builds failed.
     */
    public BuildsView failed(Failure failure) {
        return new BuildsView(
                branch,
                onGitHub,
                builds.failed(failure),
                commits,
                limit,
                after,
                false,
                builds.toOption().map(read -> failure),
                workflows,
                selected,
                allBranches,
                runs,
                repoWorkflows,
                window,
                picked);
    }

    /**
     * Whether more commits can be asked for: older ones are left, or reading the first ones failed, and none are
     * being read (ADR 132).
     */
    public boolean hasMore() {
        return !reading && (after.isDefined() || builds.isFailed());
    }

    /**
     * Whether reading the commits failed: the first ones, or more after some were read.
     */
    public boolean hasFailed() {
        return builds.isFailed() || stopped.isDefined();
    }

    /**
     * This, reading {@link #COMMITS} more commits from where those read end, or again from where reading them failed,
     * from the tip when it failed at the first; none when no older ones are left, or they are being read (ADR 132).
     */
    public Option<BuildsView> more() {
        return Option.when(
                hasMore(),
                () -> new BuildsView(
                        branch,
                        onGitHub,
                        builds.isFailed() ? Loadable.loading() : builds,
                        commits,
                        commits + COMMITS,
                        after,
                        true,
                        Option.none(),
                        workflows,
                        selected,
                        allBranches,
                        runs,
                        repoWorkflows,
                        window,
                        picked));
    }

    /**
     * This, with {@code read} of the files of workflows; a file being read is in it as none.
     */
    public BuildsView withWorkflows(Map<FileAt, Option<Workflow>> read) {
        return new BuildsView(
                branch,
                onGitHub,
                builds,
                commits,
                limit,
                after,
                reading,
                stopped,
                workflows.merge(read, (was, now) -> now),
                selected,
                allBranches,
                runs,
                repoWorkflows,
                window,
                picked);
    }

    /**
     * This, showing the builds of {@code pipeline}.
     */
    public BuildsView withSelected(Pipeline pipeline) {
        return new BuildsView(
                branch,
                onGitHub,
                builds,
                commits,
                limit,
                after,
                reading,
                stopped,
                workflows,
                Option.of(pipeline),
                allBranches,
                runs,
                repoWorkflows,
                window,
                picked);
    }

    /**
     * Of each workflow and event, the last build that is done, and of each workflow whose runs on every branch are
     * read, its last run that is done, and the build picked: those whose jobs can show on one time line.
     */
    public List<Build> lastDone() {
        List<Build> read = builds.getOrElse(List.empty());
        return read.map(Pipeline::of)
                .distinct()
                .flatMap(pipeline -> lastDoneOf(read.filter(pipeline::ran)))
                .appendAll(runs.values()
                        .toList()
                        .flatMap(ofWorkflow -> ofWorkflow.builds().toOption().flatMap(BuildsView::lastDoneOf)))
                .appendAll(pickedBuild())
                .distinctBy(Build::runId);
    }

    /**
     * The build picked, among those of the branch and the runs on every branch read; none when none is, or it is no
     * longer read (ADR 133).
     */
    public Option<Build> pickedBuild() {
        return picked.flatMap(run -> builds.getOrElse(List.empty())
                .appendAll(runs.values().toList().flatMap(read -> read.builds().getOrElse(List.empty())))
                .find(build -> build.runId() == run));
    }

    /**
     * This, with the build of the run {@code run} picked, or none picked again when it already was (ADR 133).
     */
    public BuildsView toggledPick(long run) {
        return withPicked(picked.contains(run) ? Option.none() : Option.of(run));
    }

    /**
     * This, with the build {@code by} rows after the one on the time line picked among the builds that show, the
     * newest first: an older one when positive. It stops at the newest and the oldest; with none on the time line it
     * picks the newest (ADR 133).
     */
    public BuildsView steppedPick(int by) {
        return shownOf()
                .flatMap(shown -> shown.builds()
                        .toOption()
                        .filter(builds -> !builds.isEmpty())
                        .map(builds -> {
                            Option<Integer> at = shown.picked()
                                    .flatMap(run -> builds.find(build -> build.runId() == run))
                                    .orElse(() -> lastDoneOf(builds))
                                    .map(builds::indexOf);
                            int to = at.map(from -> Math.clamp((long) from + by, 0, builds.size() - 1))
                                    .getOrElse(0);
                            return withPicked(Option.of(builds.get(to).runId()));
                        }))
                .getOrElse(this);
    }

    /**
     * The files of the workflows of the last builds that are done, that are not read yet, nor being read.
     */
    public List<FileAt> unread() {
        return lastDone()
                .flatMap(build -> build.file().map(path -> new FileAt(build.sha(), path)))
                .distinct()
                .filter(file -> !workflows.containsKey(file));
    }

    /**
     * Of each of the last builds that are done, by the id of its run, which of its jobs each job needs, as the file of
     * its workflow says at its commit; a build is left out while that is not read, or when it could not be.
     */
    public Map<Long, Map<String, Set<String>>> needs() {
        return HashMap.ofEntries(lastDone()
                .flatMap(build -> build.file()
                        .flatMap(path -> workflow(build.sha(), path))
                        .map(workflow -> Tuple.of(
                                build.runId(),
                                workflow.needs(
                                        build.ran().map(Build.Job::name), called -> workflow(build.sha(), called))))));
    }

    /**
     * The workflows {@code builds} ran, in the order their tabs show: those that ran on the newest commit with builds
     * first, then the others; each by how many builds it has, then by its name. The order stays as builds come in
     * and end, so that a key that steps through them lands where it did.
     */
    public static List<Pipeline> pipelines(List<Build> builds) {
        List<Pipeline> onTip = onTip(builds);
        Comparator<Pipeline> byCount = Comparator.<Pipeline>comparingInt(pipeline -> -builds.count(pipeline::ran))
                .thenComparing(Pipeline::workflow)
                .thenComparing(Pipeline::event);
        return onTip.sorted(byCount)
                .appendAll(builds.map(Pipeline::of)
                        .distinct()
                        .filter(pipeline -> !onTip.contains(pipeline))
                        .sorted(byCount));
    }

    /**
     * The workflow whose builds show: the one chosen, while it has builds; else, of those on the
     * newest commit with builds, one whose last build failed, else the one with the most builds. A workflow chosen on
     * every branch stands for its tab with the most builds.
     */
    public Option<Pipeline> shownPipeline() {
        List<Build> builds = loaded();
        List<Pipeline> all = pipelines(builds);
        return selected.flatMap(chosen -> all.filter(chosen::covers)
                        .maxBy(Comparator.comparingInt(pipeline -> builds.count(pipeline::ran))))
                .orElse(() -> onTip(builds)
                        .sorted(Comparator.<Pipeline, Boolean>comparing(pipeline -> builds.find(pipeline::ran)
                                        .exists(last -> last.outcome() != Build.Outcome.FAILED))
                                .thenComparing(
                                        Comparator.<Pipeline>comparingInt(pipeline -> -builds.count(pipeline::ran)))
                                .thenComparing(Pipeline::workflow)
                                .thenComparing(Pipeline::event))
                        .headOption())
                .orElse(all::headOption);
    }

    /**
     * The workflows of the tabs: of the branch, those it ran; on
     * every branch, the workflows it ran first, in the same order, then the other workflows of the repository that
     * run, by name, each of any event.
     */
    public List<Pipeline> tabs() {
        List<Build> builds = loaded();
        if (!allBranches) {
            return pipelines(builds);
        }
        List<String> ran = pipelines(builds).map(Pipeline::workflow).distinct();
        List<String> others = repoWorkflows
                .flatMap(Loadable::toOption)
                .getOrElse(List.empty())
                .filter(RepoWorkflow::active)
                .map(RepoWorkflow::name)
                .filter(name -> !ran.contains(name))
                .distinct()
                .sorted();
        return ran.appendAll(others).map(Pipeline::any);
    }

    /**
     * The tab shown: of the branch, as {@link #shownPipeline} says; on every branch, the
     * workflow chosen, else that of the tab the branch shows, else the first.
     */
    public Option<Pipeline> shownTab() {
        if (!allBranches) {
            return shownPipeline();
        }
        List<Pipeline> all = tabs();
        return selected.map(chosen -> Pipeline.any(chosen.workflow()))
                .filter(all::contains)
                .orElse(() -> shownPipeline()
                        .map(pipeline -> Pipeline.any(pipeline.workflow()))
                        .filter(all::contains))
                .orElse(all::headOption);
    }

    /**
     * This, showing the tab {@code by} tabs after the one that shows, around from the last to the
     * first.
     */
    public BuildsView stepped(int by) {
        List<Pipeline> all = tabs();
        return shownTab()
                .map(now -> all.get(Math.floorMod(all.indexOf(now) + by, all.size())))
                .map(this::withSelected)
                .getOrElse(this);
    }

    /**
     * The id on GitHub of the workflow {@code workflow}, as a build of the branch or the workflows of the repository
     * tell it; none while neither does.
     */
    public Option<String> workflowId(String workflow) {
        return builds.getOrElse(List.empty())
                .filter(build -> build.workflow().equals(workflow))
                .flatMap(build -> build.origin().workflowId())
                .headOption()
                .orElse(() -> repoWorkflows
                        .flatMap(Loadable::toOption)
                        .getOrElse(List.empty())
                        .find(known -> known.name().equals(workflow))
                        .map(RepoWorkflow::id));
    }

    /**
     * The runs on every branch of the workflow {@code workflow}, as far as they are asked for.
     */
    public Option<WorkflowRuns> runsOf(String workflow) {
        return workflowId(workflow).flatMap(runs::get);
    }

    /**
     * What the builds show: the tabs, how the last build of each
     * ended, and the builds of the tab shown, with what goes with them; none without a tab.
     */
    public Option<BuildsShown> shownOf() {
        List<Build> builds = loaded();
        List<Pipeline> all = tabs();
        Set<String> mine = HashSet.ofAll(builds.map(Build::sha));
        return shownTab().map(tab -> {
            Loadable<List<Build>> ofTab = allBranches
                    ? runsOf(tab.workflow()).map(WorkflowRuns::builds).getOrElse(Loadable::loading)
                    : new Loadable.Loaded<>(builds.filter(tab::ran));
            Loadable<List<Build>> windowed =
                    ofTab.map(read -> window.map(read::take).getOrElse(read));
            return new BuildsShown(
                    all,
                    all.map(each -> lastOf(each, builds)),
                    all.indexOf(tab),
                    allBranches,
                    windowed,
                    ofTab.toOption().map(List::size).getOrElse(0),
                    allBranches ? mine : HashSet.empty(),
                    allBranches ? Option.none() : baseline(tab),
                    needs(),
                    // The build picked, while it is among those that show: else the last that is done shows.
                    picked.filter(run ->
                            windowed.toOption().exists(shown -> shown.exists(build -> build.runId() == run))));
        });
    }

    /**
     * How the last build of {@code tab} ended: of the branch, among {@code builds}; on every branch, among its runs,
     * once read.
     */
    private Option<Build.Outcome> lastOf(Pipeline tab, List<Build> builds) {
        return allBranches
                ? runsOf(tab.workflow())
                        .flatMap(read -> read.builds().toOption())
                        .flatMap(read -> read.headOption())
                        .map(Build::outcome)
                : builds.find(tab::ran).map(Build::outcome);
    }

    /**
     * How long the runs of {@code tab}'s workflow and event that passed took on every branch, once they are read.
     */
    private Option<BuildsShown.Baseline> baseline(Pipeline tab) {
        return runsOf(tab.workflow())
                .flatMap(read -> read.builds().toOption())
                .map(read -> read.filter(tab::ran)
                        .filter(build -> build.outcome() == Build.Outcome.PASSED)
                        .flatMap(Build::duration))
                .flatMap(passed -> median(passed).map(middle -> new BuildsShown.Baseline(middle, passed.size())));
    }

    /**
     * The builds read so far; none while they are not.
     */
    private List<Build> loaded() {
        return builds.getOrElse(List.empty());
    }

    private static List<Pipeline> onTip(List<Build> builds) {
        Option<String> tip = builds.headOption().map(Build::sha);
        return builds.filter(build -> tip.contains(build.sha()))
                .map(Pipeline::of)
                .distinct();
    }

    private Option<Workflow> workflow(String sha, String path) {
        return workflows.get(new FileAt(sha, path)).flatMap(read -> read);
    }

    /**
     * A file at a commit.
     *
     * @param sha  the commit
     * @param path its path
     */
    public record FileAt(String sha, String path) {}

    /**
     * A workflow as it runs for one event, like {@code Build} for {@code pull_request}, or for any: the builds of one
     * tab.
     *
     * @param workflow the name of the workflow
     * @param event    what starts it; empty for any event
     */
    public record Pipeline(String workflow, String event) {

        /**
         * The workflow and the event of {@code build}.
         */
        public static Pipeline of(Build build) {
            return new Pipeline(build.workflow(), build.event());
        }

        /**
         * The workflow {@code workflow}, for any event.
         */
        public static Pipeline any(String workflow) {
            return new Pipeline(workflow, "");
        }

        /**
         * How its tab names it among {@code all}: its workflow, and the event it runs for when that workflow runs for
         * more than one.
         */
        public String label(List<Pipeline> all) {
            return !event.isEmpty() && all.count(other -> other.workflow().equals(workflow)) > 1
                    ? workflow + " · " + event
                    : workflow;
        }

        /**
         * Whether {@code build} is one of its builds.
         */
        public boolean ran(Build build) {
            return build.workflow().equals(workflow)
                    && (event.isEmpty() || build.event().equals(event));
        }

        /**
         * Whether this, chosen, stands for {@code other}: it is, or it is its workflow for any event.
         */
        boolean covers(Pipeline other) {
            return equals(other) || event.isEmpty() && workflow.equals(other.workflow());
        }
    }
}
