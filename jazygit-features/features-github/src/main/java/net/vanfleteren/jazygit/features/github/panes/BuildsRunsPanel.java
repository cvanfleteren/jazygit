package net.vanfleteren.jazygit.features.github.panes;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.features.github.builds.BuildPages;
import net.vanfleteren.jazygit.features.github.builds.Paging;
import net.vanfleteren.jazygit.features.github.builds.RunsDrawn;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScopes;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreenMsg;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.ScreenPane;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The pane of the runs of the workflow highlighted on the builds screen, as the filter keeps them, the newest first
 * (ADR 212): how each ended, what it ran for, marked when it is of "mine" on every branch, its event, how long it took
 * against the median and how long ago. The title says which runs they are, and when more can be read with {@code m}.
 * A click on a run in the chart highlights it here.
 */
public final class BuildsRunsPanel implements LeftPane {

    private final LoadableList<Listed> list;
    private final Keymap keymap = Keymap.standard();
    // The runs last listed, so that the list is shown again only once they change.
    private Loadable<Listed> listed = Loadable.loading();
    // What failed in the run highlighted, as the model of the last render says, for the keys of a failed build.
    private Option<FailedBuild> failed = Option.none();

    private BuildsRunsPanel(Consumer<Msg> dispatch, Consumer<PaneId> openContent, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get(BuildsScopes.RUNS.titleKey()),
                BuildsScopes.RUNS.id(),
                BuildsRunsPanel::reselect,
                highlights);
        list.onKey(event -> event.isConfirm()
                ? Option.of(() -> openContent.accept(BuildsScreen.RUNS_PANE))
                : PaneKeys.sending(
                        keymap.handle(
                                BuildsScopes.RUNS, event, selected(), new Keymap.Hosted<>(Scopes.FAILED_BUILD, failed)),
                        dispatch));
    }

    /**
     * The pane, as the builds screen brings it.
     */
    public static LeftPane of(ScreenPane.Context context) {
        return new BuildsRunsPanel(context.dispatch(), context.openContent(), context.highlights());
    }

    @Override
    public PaneId pane() {
        return BuildsScreen.RUNS_PANE;
    }

    @Override
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        BuildsScreen screen = model.part(BuildsScreen.PART);
        String workflow = screen.shown().map(RepoWorkflow::name).getOrElse("");
        Loadable<Listed> now = screen.filtered().map(runs -> new Listed(workflow, runs, screen.clicked()));
        if (!now.equals(listed)) {
            listed = now;
        }
        RunsDrawn drawn = screen.drawn(model);
        Option<java.time.Duration> median = RunsDrawn.median(screen.filtered()
                .getOrElse(List.empty())
                .filter(build -> build.outcome() == Build.Outcome.PASSED)
                .flatMap(Build::duration));
        list.whenEmpty(empty(model, screen, workflow));
        Panel panel = list.render(
                listed,
                read -> read.runs()
                        .map(run -> new LoadableList.Row(RunRows.row(run, median, drawn.mine(), model.now()))),
                focus.contains(pane()),
                inUse.contains(pane()),
                Pane.title(title(screen, workflow)));
        failed = BuildsScopes.failed(model, selected());
        return panel;
    }

    @Override
    public Option<Msg> selectionChange(Model model) {
        return selected()
                .filter(run -> !model.part(BuildsScreen.PART).run().contains(run.runId()))
                .map(run -> new BuildsScreenMsg.RunHighlighted(run.runId()));
    }

    private Option<Build> selected() {
        return list.selected(Listed::runs);
    }

    /**
     * Where the highlight goes as the runs change: to the newest for another workflow, to the run clicked in the
     * chart after a click, else with its run.
     */
    static int reselect(Listed previous, Listed next, int selected) {
        if (!previous.workflow().equals(next.workflow())) {
            return 0;
        }
        return next.clicked()
                .filter(click -> !previous.clicked().contains(click))
                .map(click -> next.runs().indexWhere(run -> run.runId() == click.run()))
                .filter(index -> index >= 0)
                .getOrElse(() -> LoadableList.Reselect.<Build, Long>following(Build::runId)
                        .apply(previous.runs(), next.runs(), selected));
    }

    /**
     * The title: which runs show, and whether more can be read with {@code m}, or again after reading them failed.
     */
    static String title(BuildsScreen screen, String workflow) {
        String which = switch (screen.filter()) {
            case EVERY_BRANCH -> Messages.get("github.buildsScreen.runs.every", workflow);
            case DEFAULT_BRANCH ->
                Messages.get(
                        "github.buildsScreen.runs.on",
                        workflow,
                        screen.defaultName().map(name -> name.value()).getOrElse(""));
            case MINE ->
                Messages.get(
                        "github.buildsScreen.runs.mine",
                        workflow,
                        screen.mine().map(mine -> mine.local().value()).getOrElse(""));
        };
        Option<BuildPages> pages = screen.pages();
        boolean more = pages.exists(BuildPages::hasMore);
        boolean reading = pages.exists(read -> read.paging() instanceof Paging.Reading && read.count() > 0);
        return reading
                ? Messages.get("github.buildsScreen.runs.reading", which)
                : more
                        ? Messages.get(
                                pages.exists(BuildPages::hasFailed)
                                        ? "github.builds.panel.retry"
                                        : "github.builds.panel.more",
                                which)
                        : which;
    }

    /**
     * What the pane says while the filter keeps no runs of {@code workflow}.
     */
    private static String empty(Model model, BuildsScreen screen, String workflow) {
        return switch (screen.filter()) {
            case EVERY_BRANCH -> Messages.get("github.buildsScreen.runs.noneEvery", workflow);
            case DEFAULT_BRANCH ->
                Messages.get(
                        "github.buildsScreen.runs.noneOn",
                        workflow,
                        screen.defaultName().map(name -> name.value()).getOrElse(""),
                        String.valueOf(screen.onDefault().count()));
            case MINE ->
                BuildsScreen.mineOf(model)
                        .fold(
                                missing -> ErrorText.of(missing.key()),
                                mine -> Messages.get(
                                        "github.buildsScreen.runs.noneOn",
                                        workflow,
                                        mine.local().value(),
                                        String.valueOf(screen.mine()
                                                .map(read -> read.pages().count())
                                                .getOrElse(0))));
        };
    }

    /**
     * The runs the pane lists, of {@code workflow}, with the last click in the chart, which it moves to.
     *
     * @param workflow the workflow they are of
     * @param runs     the runs, the newest first
     * @param clicked  the last click on a run in the chart, if any
     */
    record Listed(String workflow, List<Build> runs, Option<BuildsScreen.Clicked> clicked) {}
}
