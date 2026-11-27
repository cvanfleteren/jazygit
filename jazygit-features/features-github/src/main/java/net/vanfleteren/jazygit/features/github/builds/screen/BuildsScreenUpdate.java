package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.DefaultBranchPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.builds.BuildPages;
import net.vanfleteren.jazygit.features.github.builds.FileAt;
import net.vanfleteren.jazygit.features.github.builds.Paging;
import net.vanfleteren.jazygit.features.github.builds.Workflow;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenUpdate;

/**
 * The builds screen (ADR 212): what the user does on it, and reading what it shows, only while it is shown. Each change
 * reads what the screen then shows and is not read nor being read: the workflows, the default branch, the runs of the
 * workflow highlighted or the builds of "mine", and the files of the workflow of the run on the time line. Looking
 * again, when it is shown and each minute, reads the newest of each again, keeping the older ones read.
 */
public final class BuildsScreenUpdate {

    // The reads, as the freshness of the screen names them.
    private static final String WORKFLOWS = "workflows";
    private static final String DEFAULT = "default";
    private static final String RUNS = "runs";
    private static final String MINE = "mine";

    private BuildsScreenUpdate() {}

    public static Next update(Model model, BuildsScreenMsg msg) {
        return switch (msg) {
            case BuildsScreenMsg.Tick() -> shown(model) ? lookAgain(model) : Next.of(model);
            case BuildsScreenMsg.RunningTick() -> shown(model) && running(model) ? newestAgain(model) : Next.of(model);
            case BuildsScreenMsg.Opened(BranchName branch) ->
                ensured(ScreenUpdate.update(
                        changed(
                                model,
                                screen -> screen.withChosen(Option.of(branch))
                                        .withFilter(BuildsScreen.Filter.MINE)
                                        .withRun(Option.none())),
                        new ScreenMsg.Shown(BuildsScreen.SCREEN)));
            case BuildsScreenMsg.WorkflowHighlighted(String workflow) ->
                ensured(Next.of(changed(
                        model,
                        screen -> screen.highlighted().contains(workflow)
                                ? screen
                                : screen.withHighlighted(Option.of(workflow)).withRun(Option.none()))));
            case BuildsScreenMsg.RunHighlighted(long run) ->
                ensured(Next.of(changed(model, screen -> screen.withRun(Option.of(run)))));
            case BuildsScreenMsg.RunClicked(long run) ->
                ensured(Next.of(changed(
                        model,
                        screen -> screen.withRun(Option.of(run))
                                .withClicked(Option.of(new BuildsScreen.Clicked(
                                        run,
                                        screen.clicked()
                                                        .map(BuildsScreen.Clicked::count)
                                                        .getOrElse(0)
                                                + 1))))));
            case BuildsScreenMsg.FilterRequested() -> PopupUpdate.ask(model, filterPopup(model));
            case BuildsScreenMsg.FilterPicked(BuildsScreen.Filter filter) -> picked(model, filter);
            case BuildsScreenMsg.WindowRequested() ->
                PopupUpdate.ask(model, new ChartPopup(screen(model).window()));
            case BuildsScreenMsg.WindowPicked(Option<Integer> runs) ->
                Next.of(changed(model.withoutPopup(ChartPopup.class), screen -> screen.withWindow(runs)));
            case BuildsScreenMsg.MoreRequested() -> more(model);
            case BuildsScreenMsg.BrowseRequested(Build run) ->
                Next.of(model, new BrowseRunCmd(run.runId(), run.workflow() + " · " + run.title()));
            case BuildsScreenMsg.RerunRequested(Build run) ->
                rerunnable(run)
                        ? Next.of(model, new RerunRunCmd(run.runId(), run.workflow() + " · " + run.title()))
                        : Next.of(model);
            case BuildsScreenMsg.WorkflowsRead(var workflows) ->
                ensured(Next.of(changed(
                        model,
                        screen -> screen.withWorkflows(new Loadable.Loaded<>(workflows))
                                .withFreshness(screen.freshness().succeeded(WORKFLOWS, model.now())))));
            case BuildsScreenMsg.WorkflowsFailed(var failure) ->
                Next.of(changed(
                        model,
                        screen -> screen.withWorkflows(
                                        loaded(screen.workflows())
                                                ? screen.workflows()
                                                : new Loadable.Failed<>(failure))
                                .withFreshness(screen.freshness().failed(WORKFLOWS))));
            case BuildsScreenMsg.DefaultRead(var after, boolean refresh, var page) ->
                defaultRead(model, after, refresh, page);
            case BuildsScreenMsg.DefaultFailed(var after, boolean refresh, var failure) ->
                Next.of(changed(
                        model,
                        screen -> (refresh
                                        ? screen
                                        : screen.withOnDefault(
                                                screen.onDefault().awaits(after)
                                                        ? screen.onDefault().failed(failure)
                                                        : screen.onDefault()))
                                .withFreshness(screen.freshness().failed(DEFAULT))));
            case BuildsScreenMsg.BranchRead(var branch, var after, boolean refresh, var page) ->
                mineRead(
                        model,
                        branch,
                        after,
                        refresh,
                        pages -> refresh
                                ? pages.refreshed(
                                        page.map(read -> read.builds()).getOrElse(List.empty()))
                                : pages.readCommits(page),
                        fresh -> fresh.succeeded(MINE, model.now()));
            case BuildsScreenMsg.BranchFailed(var branch, var after, boolean refresh, var failure) ->
                mineRead(
                        model,
                        branch,
                        after,
                        refresh,
                        pages -> refresh ? pages : pages.failed(failure),
                        fresh -> fresh.failed(MINE));
            case BuildsScreenMsg.RunsRead(String id, var after, boolean refresh, var page) ->
                runsRead(
                        model,
                        id,
                        after,
                        refresh,
                        pages -> refresh
                                ? pages.refreshed(
                                        page.map(read -> read.builds()).getOrElse(List.empty()))
                                : pages.read(page),
                        fresh -> fresh.succeeded(RUNS, model.now()));
            case BuildsScreenMsg.RunsFailed(String id, var after, boolean refresh, var failure) ->
                runsRead(
                        model,
                        id,
                        after,
                        refresh,
                        pages -> refresh ? pages : pages.failed(failure),
                        fresh -> fresh.failed(RUNS));
            case BuildsScreenMsg.InProgressRead(int runs) ->
                Next.of(changed(model, screen -> screen.withInProgress(Option.of(runs))));
            case BuildsScreenMsg.InProgressFailed _ -> Next.of(model);
            case BuildsScreenMsg.FilesRead(var files) ->
                ensured(Next.of(changed(model, screen -> screen.withRead(files))));
        };
    }

    /**
     * What the builds screen answers of what happens in the kernel (ADR 212): it looks again each time it is shown.
     * GitHub's feature declares it.
     */
    public static Next to(Model model, KernelEvent event) {
        return event instanceof KernelEvent.ScreenShown(Screen screen) && screen.equals(BuildsScreen.SCREEN)
                ? lookAgain(model)
                : Next.of(model);
    }

    /**
     * Whether failed jobs of {@code run} can run again: it is done, and it failed.
     */
    public static boolean rerunnable(Build run) {
        return run.outcome() == Build.Outcome.FAILED;
    }

    private static boolean shown(Model model) {
        return model.screen().equals(BuildsScreen.SCREEN);
    }

    private static BuildsScreen screen(Model model) {
        return model.part(BuildsScreen.PART);
    }

    private static Model changed(Model model, UnaryOperator<BuildsScreen> change) {
        return model.withPart(BuildsScreen.PART, change.apply(screen(model)));
    }

    /**
     * Whether one of the runs the filter keeps runs, so it is read again sooner.
     */
    private static boolean running(Model model) {
        return screen(model)
                .filtered()
                .getOrElse(List.empty())
                .exists(build -> build.outcome() == Build.Outcome.RUNNING);
    }

    /**
     * {@code model}, reading again the workflows, the newest commits of the default branch, how many runs are in
     * progress, and the newest of what the filter shows; then what is not read yet.
     */
    private static Next lookAgain(Model model) {
        BuildsScreen screen = screen(model);
        List<Cmd> again = List.<Cmd>of(new LoadScreenWorkflows(), new CountRunsInProgress())
                .appendAll(
                        Option.when(loaded(screen.onDefault().builds()), new LoadDefaultBuilds(Option.none(), true)));
        return ensured(newestAgain(model).and(notRunning(model, again)));
    }

    /**
     * {@code model}, reading again the newest of what the filter shows, once some of it was read.
     */
    private static Next newestAgain(Model model) {
        BuildsScreen screen = screen(model);
        Option<Cmd> again = switch (screen.filter()) {
            case EVERY_BRANCH ->
                screen.shown()
                        .filter(workflow -> screen.runs().get(workflow.id()).exists(pages -> loaded(pages.builds())))
                        .map(workflow -> new LoadScreenRuns(workflow.id(), Option.none(), true));
            case DEFAULT_BRANCH ->
                Option.when(loaded(screen.onDefault().builds()), new LoadDefaultBuilds(Option.none(), true));
            case MINE ->
                screen.mine()
                        .filter(mine -> loaded(mine.pages().builds()))
                        .map(mine -> new LoadBranchBuilds(mine.remote(), Option.none(), true));
        };
        return Next.of(model).and(notRunning(model, again.toList()));
    }

    /**
     * {@code next}, also reading what the builds screen shows of its model and is not read nor being read, while it is
     * shown.
     */
    private static Next ensured(Next next) {
        return next.then(model -> shown(model) ? ensure(model) : Next.of(model));
    }

    private static Next ensure(Model model) {
        BuildsScreen screen = screen(model);
        List<Cmd> workflows = Option.<Cmd>when(loading(screen.workflows()), new LoadScreenWorkflows())
                .toList();
        List<Cmd> onDefault = Option.<Cmd>when(
                        loading(screen.onDefault().builds())
                                && screen.onDefault().awaits(Option.none()),
                        new LoadDefaultBuilds(Option.none(), false))
                .toList();
        Tuple2<Model, List<Cmd>> filtered = askedForFilter(model);
        Model asked = filtered._1();
        BuildsScreen now = screen(asked);
        List<FileAt> files = now.unread(asked);
        Model reading = files.isEmpty()
                ? asked
                : changed(
                        asked,
                        each -> each.withRead(
                                HashMap.ofEntries(files.map(file -> Tuple.of(file, Option.<Workflow>none())))));
        List<Cmd> cmds =
                workflows.appendAll(onDefault).appendAll(filtered._2()).appendAll(files.map(ReadScreenFiles::new));
        return Next.of(reading).and(notRunning(model, cmds));
    }

    /**
     * {@code model} asking for what the filter shows and is not asked for yet: the runs of the workflow highlighted on
     * every branch, or the builds of "mine", for its branch now; with the reads that ask for it.
     */
    private static Tuple2<Model, List<Cmd>> askedForFilter(Model model) {
        BuildsScreen screen = screen(model);
        return switch (screen.filter()) {
            case EVERY_BRANCH ->
                screen.shown()
                        .map(RepoWorkflow::id)
                        .filter(id -> !screen.runs().containsKey(id))
                        .map(id -> Tuple.<Model, List<Cmd>>of(
                                changed(
                                        model,
                                        now -> now.withRuns(now.runs().put(id, BuildPages.asked(BuildPages.RUNS)))),
                                List.of(new LoadScreenRuns(id, Option.none(), false))))
                        .getOrElse(() -> Tuple.of(model, List.empty()));
            case MINE ->
                BuildsScreen.mineOf(model)
                        .toOption()
                        .filter(found -> !screen.mine()
                                .exists(read -> read.local().equals(found.local())
                                        && read.remote().equals(found.remote())))
                        .map(found -> Tuple.<Model, List<Cmd>>of(
                                changed(
                                        model,
                                        now -> now.withMine(Option.of(new BuildsScreen.Mine(
                                                found.local(), found.remote(), BuildPages.asked(BuildPages.RUNS))))),
                                List.of(new LoadBranchBuilds(found.remote(), Option.none(), false))))
                        .getOrElse(() -> Tuple.of(model, List.empty()));
            case DEFAULT_BRANCH -> Tuple.of(model, List.empty());
        };
    }

    private static boolean loaded(Loadable<?> value) {
        return value.toOption().isDefined();
    }

    private static boolean loading(Loadable<?> value) {
        return value instanceof Loadable.Loading<?>;
    }

    private static List<Cmd> notRunning(Model model, List<Cmd> cmds) {
        return cmds.filter(cmd -> !model.jobs().runs(cmd)).distinct();
    }

    /**
     * {@code model} showing the runs of {@code filter}; "mine" only while there is a branch for it.
     */
    private static Next picked(Model model, BuildsScreen.Filter filter) {
        Model closed = model.withoutPopup(FilterPopup.class);
        return filter == BuildsScreen.Filter.MINE && BuildsScreen.mineOf(model).isLeft()
                ? Next.of(closed)
                : ensured(Next.of(
                        changed(closed, screen -> screen.withFilter(filter).withRun(Option.none()))));
    }

    private static FilterPopup filterPopup(Model model) {
        BuildsScreen screen = screen(model);
        return new FilterPopup(
                screen.filter(),
                screen.defaultName().map(BranchName::value).getOrElse(""),
                BuildsScreen.mineOf(model).map(found -> found.local().value()).mapLeft(BuildsScreen.Missing::key));
    }

    /**
     * {@code model}, reading older runs than those read of what the filter shows, when older ones are left and none
     * are being read; or again after reading them failed.
     */
    private static Next more(Model model) {
        BuildsScreen screen = screen(model);
        return switch (screen.filter()) {
            case EVERY_BRANCH ->
                screen.shown()
                        .flatMap(workflow -> screen.runs()
                                .get(workflow.id())
                                .flatMap(BuildPages::more)
                                .map(pages -> Next.of(
                                        changed(
                                                model,
                                                now -> now.withRuns(now.runs().put(workflow.id(), pages))),
                                        new LoadScreenRuns(
                                                workflow.id(), pages.paging().from(), false))))
                        .getOrElse(() -> Next.of(model));
            case DEFAULT_BRANCH ->
                screen.onDefault()
                        .more()
                        .map(pages -> Next.of(
                                changed(model, now -> now.withOnDefault(pages)),
                                new LoadDefaultBuilds(pages.paging().from(), false)))
                        .getOrElse(() -> Next.of(model));
            case MINE ->
                screen.mine()
                        .flatMap(mine -> mine.pages()
                                .more()
                                .map(pages -> Next.of(
                                        changed(
                                                model,
                                                now -> now.withMine(Option.of(
                                                        new BuildsScreen.Mine(mine.local(), mine.remote(), pages)))),
                                        new LoadBranchBuilds(
                                                mine.remote(), pages.paging().from(), false))))
                        .getOrElse(() -> Next.of(model));
        };
    }

    private static Next defaultRead(
            Model model, Option<String> after, boolean refresh, Option<DefaultBranchPage> page) {
        BuildsScreen screen = screen(model);
        BuildPages before = screen.onDefault();
        if (!refresh && !before.awaits(after)) {
            return Next.of(model);
        }
        BuildPages now = refresh && loaded(before.builds())
                ? before.refreshed(page.map(read -> read.page().builds()).getOrElse(List.empty()))
                : before.readCommits(page.map(DefaultBranchPage::page));
        Model read = changed(
                model,
                each -> each.withOnDefault(now)
                        .withDefaultName(page.map(DefaultBranchPage::branch).orElse(each.defaultName()))
                        .withFreshness(each.freshness().succeeded(DEFAULT, model.now())));
        Next shown = ensured(Next.of(read));
        return !refresh && now.paging() instanceof Paging.Reading(Option<String> from)
                ? shown.and(new LoadDefaultBuilds(from, false))
                : shown;
    }

    /**
     * {@code model} with what {@code read} makes of the builds of "mine" from {@code after} on, when they are awaited
     * or {@code refresh} reads the newest again, and its freshness as {@code fresh} says; reading on while more are
     * to be read.
     */
    private static Next mineRead(
            Model model,
            RemoteBranch branch,
            Option<String> after,
            boolean refresh,
            UnaryOperator<BuildPages> read,
            UnaryOperator<Freshness> fresh) {
        return screen(model)
                .mine()
                .filter(mine ->
                        mine.remote().equals(branch) && (refresh || mine.pages().awaits(after)))
                .map(mine -> {
                    BuildPages now = read.apply(mine.pages());
                    Next shown = ensured(Next.of(changed(
                            model,
                            screen -> screen.withMine(
                                            Option.of(new BuildsScreen.Mine(mine.local(), mine.remote(), now)))
                                    .withFreshness(fresh.apply(screen.freshness())))));
                    return !refresh && now.paging() instanceof Paging.Reading(Option<String> from)
                            ? shown.and(new LoadBranchBuilds(branch, from, false))
                            : shown;
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code model} with what {@code read} makes of the runs of the workflow {@code id} from {@code after} on, when
     * they are awaited or {@code refresh} reads the newest again, and its freshness as {@code fresh} says; reading on
     * while more are to be read.
     */
    private static Next runsRead(
            Model model,
            String id,
            Option<String> after,
            boolean refresh,
            UnaryOperator<BuildPages> read,
            UnaryOperator<Freshness> fresh) {
        return screen(model)
                .runs()
                .get(id)
                .filter(pages -> refresh || pages.awaits(after))
                .map(pages -> {
                    BuildPages now = read.apply(pages);
                    Next shown = ensured(Next.of(changed(
                            model,
                            screen -> screen.withRuns(screen.runs().put(id, now))
                                    .withFreshness(fresh.apply(screen.freshness())))));
                    return !refresh && now.paging() instanceof Paging.Reading(Option<String> from)
                            ? shown.and(new LoadScreenRuns(id, from, false))
                            : shown;
                })
                .getOrElse(() -> Next.of(model));
    }
}
