package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.Comparator;
import lombok.With;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.GitHubId;
import net.vanfleteren.jazygit.features.github.builds.BuildPages;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.FileAt;
import net.vanfleteren.jazygit.features.github.builds.RunsDrawn;
import net.vanfleteren.jazygit.features.github.builds.Workflow;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;

/**
 * What the builds screen shows (ADR 212): the workflows of the repository, the builds on the default branch, and, as
 * the filter asks for them, the runs of a workflow on every branch or the builds of one branch. It keeps, for as long
 * as jazygit runs, the workflow highlighted, the filter and how many runs the chart shows.
 *
 * @param workflows   the workflows of the repository, once read
 * @param defaultName the default branch, once its builds were read
 * @param onDefault   the builds on the newest commits of the default branch
 * @param mine        the branch of the filter "mine" and the builds on its commits, once that filter asked for them
 * @param chosen      the local branch {@code B} was pressed on, which is "mine"; none for the one checked out
 * @param runs        the runs on every branch of the workflows asked for, by the id of the workflow
 * @param files       the files of workflows read at the commit of a build, which say which job needs which; none
 *                    for one being read, or that could not be
 * @param highlighted the workflow highlighted, as the user left it; none for the one the screen starts with
 * @param filter      which runs of it show
 * @param window      how many of the newest runs the chart shows; none for as many as fit
 * @param run         the run highlighted in the runs pane, while it is in use
 * @param inProgress  how many runs are in progress on any branch, once counted
 * @param freshness   when what it shows was last read, and which reads failed the last time
 * @param clicked     the run last clicked in the chart, for the runs pane to highlight, and how many clicks there
 *                    were, so that one on the same run again moves the cursor to it again
 */
@With
public record BuildsScreen(
        Loadable<List<RepoWorkflow>> workflows,
        Option<BranchName> defaultName,
        BuildPages onDefault,
        Option<BuildsScreen.Mine> mine,
        Option<BranchName> chosen,
        Map<String, BuildPages> runs,
        Map<FileAt, Option<Workflow>> files,
        Option<String> highlighted,
        BuildsScreen.Filter filter,
        Option<Integer> window,
        Option<Long> run,
        Option<Integer> inProgress,
        Freshness freshness,
        Option<BuildsScreen.Clicked> clicked) {

    /**
     * The commits of the default branch read each time the screen looks again: one call (ADR 212).
     */
    public static final int DEFAULT_COMMITS = 25;

    /**
     * The pane of the workflows, on top.
     */
    public static final PaneId WORKFLOWS_PANE = new PaneId("buildsWorkflows");

    /**
     * The pane of the runs of the workflow highlighted, which the screen starts with.
     */
    public static final PaneId RUNS_PANE = new PaneId("buildsRuns");

    /**
     * How wide the left column of the screen is kept.
     */
    public static final SettingKey<Integer> WIDTH = Settings.leftWidth("ui.builds-width");

    /**
     * How tall the workflows are kept, in percent of the left column: about seven rows; the runs take what is left.
     */
    public static final SettingKey<List<Integer>> HEIGHTS = Settings.leftHeights("ui.builds-heights", List.of(25));

    /**
     * The screen of the builds, which GitHub's feature brings (ADR 212): the workflows, then their runs, which it
     * opens on.
     */
    public static final Screen SCREEN = new Screen(
                    "builds", List.of(WORKFLOWS_PANE, RUNS_PANE), WIDTH, HEIGHTS, Option.of(GitHubId.ID))
            .opensOn(RUNS_PANE);

    public static final BuildsScreen INITIAL = new BuildsScreen(
            Loadable.loading(),
            Option.none(),
            BuildPages.asked(DEFAULT_COMMITS),
            Option.none(),
            Option.none(),
            HashMap.empty(),
            HashMap.empty(),
            Option.none(),
            Filter.EVERY_BRANCH,
            Option.none(),
            Option.none(),
            Option.none(),
            Freshness.NEVER,
            Option.none());

    /**
     * Where the model keeps what the screen shows, as a part of GitHub's feature (ADR 157).
     */
    public static final Part<BuildsScreen> PART = Part.of("github.buildsScreen", INITIAL);

    /**
     * The active workflows of the repository, in the order the workflows pane lists them: those that ran on the
     * newest commit of the default branch with builds first, then the others, each by how many builds it has on the
     * default branch, then by name (ADR 131, 212).
     */
    public List<RepoWorkflow> listed() {
        List<Build> builds = defaultBuilds();
        Option<Sha> tip = builds.headOption().map(Build::sha);
        List<String> onTip = builds.filter(build -> tip.contains(build.sha())).map(Build::workflow);
        return workflows
                .getOrElse(List.empty())
                .filter(RepoWorkflow::active)
                .distinctBy(RepoWorkflow::name)
                .sorted(Comparator.<RepoWorkflow, Boolean>comparing(workflow -> !onTip.contains(workflow.name()))
                        .thenComparing(workflow ->
                                -builds.count(build -> build.workflow().equals(workflow.name())))
                        .thenComparing(RepoWorkflow::name));
    }

    /**
     * The workflow highlighted: the one the user left highlighted, while it is listed; else, of those on the newest
     * commit of the default branch, one whose last build failed, else the one with the most builds; else the first.
     */
    public Option<RepoWorkflow> shown() {
        List<RepoWorkflow> all = listed();
        List<Build> builds = defaultBuilds();
        Option<Sha> tip = builds.headOption().map(Build::sha);
        return highlighted
                .flatMap(name -> all.find(workflow -> workflow.name().equals(name)))
                .orElse(() -> all.filter(workflow -> builds.exists(build ->
                                tip.contains(build.sha()) && build.workflow().equals(workflow.name())))
                        .find(workflow ->
                                lastOnDefault(workflow.name()).exists(last -> last.outcome() == Build.Outcome.FAILED)))
                .orElse(all::headOption);
    }

    /**
     * The last build of {@code workflow} on the default branch that is done, among the commits read.
     */
    public Option<Build> lastOnDefault(String workflow) {
        return defaultBuilds()
                .filter(build -> build.workflow().equals(workflow))
                .find(build -> build.outcome() != Build.Outcome.RUNNING);
    }

    /**
     * Whether the last build on the default branch of any workflow failed.
     */
    public boolean failingOnDefault() {
        return listed().exists(workflow ->
                lastOnDefault(workflow.name()).exists(last -> last.outcome() == Build.Outcome.FAILED));
    }

    /**
     * The runs of the workflow highlighted that the filter keeps, the newest first, as far as they are read; loading
     * while the workflows or they are not read yet.
     */
    public Loadable<List<Build>> filtered() {
        return shown().map(workflow -> switch (filter) {
                    case EVERY_BRANCH ->
                        runs.get(workflow.id()).map(BuildPages::builds).getOrElse(Loadable::loading);
                    case DEFAULT_BRANCH -> onDefault.builds().map(read -> ofWorkflow(read, workflow.name()));
                    case MINE ->
                        mine.map(branch -> branch.pages().builds().map(read -> ofWorkflow(read, workflow.name())))
                                .getOrElse(() -> new Loadable.Loaded<>(List.empty()));
                })
                .getOrElse(() -> workflows.map(read -> List.<Build>empty()));
    }

    /**
     * The pages that what the filter shows is read in: of the runs of the workflow highlighted, of the default branch,
     * or of "mine"; none while they are not asked for.
     */
    public Option<BuildPages> pages() {
        return switch (filter) {
            case EVERY_BRANCH -> shown().flatMap(workflow -> runs.get(workflow.id()));
            case DEFAULT_BRANCH -> Option.of(onDefault);
            case MINE -> mine.map(Mine::pages);
        };
    }

    /**
     * The run whose jobs show on one time line: the one highlighted in the runs pane while it is in use and among
     * those the filter keeps; else the last that is done (ADR 133, 212).
     */
    public Option<Build> onTimeLine(Model model) {
        List<Build> shownRuns = filtered().getOrElse(List.empty());
        return run.filter(
                        any -> model.paneInUse(SCREEN).getOrElse(SCREEN.first()).equals(RUNS_PANE))
                .flatMap(picked -> shownRuns.find(build -> build.runId() == picked))
                .orElse(() -> BuildsView.lastDoneOf(shownRuns));
    }

    /**
     * What the content panel draws of the runs the filter keeps (ADR 212): marked, on every branch, those on the
     * commits of "mine"; held, on one branch, against how long the workflow takes on every branch, once those runs are
     * read.
     */
    public RunsDrawn drawn(Model model) {
        Loadable<List<Build>> shownRuns = filtered();
        Option<Build> onLine = onTimeLine(model);
        boolean every = filter == Filter.EVERY_BRANCH;
        return new RunsDrawn(
                shown().map(RepoWorkflow::name).getOrElse(""),
                shownRuns,
                shownRuns.toOption().map(List::size).getOrElse(0),
                every,
                every ? mineCommits(model, shownRuns.getOrElse(List.empty())) : HashSet.empty(),
                every ? Option.none() : baseline(),
                needs(onLine),
                onLine.map(Build::runId),
                window);
    }

    /**
     * The files of workflows the run on the time line needs to say which job needs which, not read yet nor being read.
     */
    public List<FileAt> unread(Model model) {
        return onTimeLine(model)
                .flatMap(build -> build.file().map(path -> new FileAt(build.sha(), path)))
                .filter(file -> !files.containsKey(file))
                .toList();
    }

    /**
     * This, with the files of {@code read} merged into those read; a file being read is in it as none.
     */
    public BuildsScreen withRead(Map<FileAt, Option<Workflow>> read) {
        return withFiles(files.merge(read, (was, now) -> now));
    }

    /**
     * The branch of the filter "mine": the local branch {@code B} was pressed on, else the one checked out, and the
     * branch on GitHub it follows; else why there is none.
     */
    public static Either<Missing, Target> mineOf(Model model) {
        Option<Branch> local = model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> model.part(PART)
                        .chosen()
                        .flatMap(name -> branches.find(branch -> branch.name().equals(name)))
                        .orElse(() -> branches.find(Branch::current)));
        return local.map(branch -> branch.remoteUpstream()
                        .filter(remote -> model.part(GitHubState.PART).mayBeOnGitHub(remote))
                        .map(remote -> new Target(branch.name(), remote))
                        .toEither(() -> new Missing(Phrase.of(
                                "github.buildsScreen.mine.offGitHub",
                                branch.name().value()))))
                .getOrElse(() -> Either.left(new Missing(Phrase.of("github.buildsScreen.mine.none"))));
    }

    /**
     * The builds on the default branch read, the newest first.
     */
    public List<Build> defaultBuilds() {
        return onDefault.builds().getOrElse(List.empty());
    }

    /**
     * The commits of {@code builds} run for the branch of "mine", whose runs are marked among those of every branch.
     */
    private static Set<Sha> mineCommits(Model model, List<Build> builds) {
        return mineOf(model)
                .map(target -> HashSet.ofAll(builds.filter(build ->
                                build.origin().branch().contains(target.remote().branch()))
                        .map(Build::sha)))
                .getOrElse(HashSet.empty());
    }

    /**
     * How long the runs of the workflow highlighted that passed took on every branch, once they are read.
     */
    private Option<RunsDrawn.Baseline> baseline() {
        return shown().flatMap(workflow -> runs.get(workflow.id()))
                .flatMap(read -> read.builds().toOption())
                .map(read -> read.filter(build -> build.outcome() == Build.Outcome.PASSED)
                        .flatMap(Build::duration))
                .flatMap(passed ->
                        BuildsView.median(passed).map(middle -> new RunsDrawn.Baseline(middle, passed.size())));
    }

    /**
     * Of {@code build}, by the id of its run, which of its jobs each needs, as the file of its workflow says at its
     * commit, once that is read.
     */
    private Map<Long, Map<String, Set<String>>> needs(Option<Build> build) {
        return HashMap.ofEntries(build.flatMap(one -> one.file()
                        .flatMap(path -> file(one.sha(), path))
                        .map(workflow -> Tuple.of(
                                one.runId(),
                                workflow.needs(one.ran().map(Build.Job::name), called -> file(one.sha(), called)))))
                .toList());
    }

    private Option<Workflow> file(Sha sha, String path) {
        return files.get(new FileAt(sha, path)).flatMap(read -> read);
    }

    private static List<Build> ofWorkflow(List<Build> builds, String workflow) {
        return builds.filter(build -> build.workflow().equals(workflow));
    }

    /**
     * Which runs of the workflow highlighted show.
     */
    public enum Filter {
        /** Its runs on every branch. */
        EVERY_BRANCH,
        /** Those on the commits of the default branch. */
        DEFAULT_BRANCH,
        /** Those on the commits of one branch: the one {@code B} was pressed on, else the one checked out. */
        MINE
    }

    /**
     * The branch of the filter "mine".
     *
     * @param local  the local branch
     * @param remote the branch on GitHub it follows
     */
    public record Target(BranchName local, RemoteBranch remote) {}

    /**
     * Why there is no branch for the filter "mine".
     *
     * @param key what says why
     */
    public record Missing(Phrase key) {}

    /**
     * A click on a run in the chart.
     *
     * @param run   the run clicked
     * @param count how many clicks there were in the chart
     */
    public record Clicked(long run, int count) {}

    /**
     * The branch of the filter "mine", and the builds on its commits.
     *
     * @param local  the local branch
     * @param remote the branch on GitHub it follows, whose builds are read
     * @param pages  the builds read on its commits
     */
    public record Mine(BranchName local, RemoteBranch remote, BuildPages pages) {}
}
