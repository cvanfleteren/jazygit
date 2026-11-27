package net.vanfleteren.jazygit.features.github.builds.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.features.github.builds.RunsDrawn;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScopes;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;

/**
 * The runs of the workflow highlighted on the builds screen, as its filter keeps them (ADR 212): how the default branch
 * did, then the summary, the chart, the jobs and the run on the time line.
 */
public record BuildsScreenSource() implements ContentSource {

    /**
     * It shows the runs of the workflow highlighted, whichever pane of the builds screen is in use.
     */
    public static final ContentArea AREA = new ContentArea(
            new AreaId("builds-screen"),
            model -> model.paneInUse(BuildsScreen.SCREEN).getOrElse(BuildsScreen.SCREEN.first()));

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(pane.equals(Mirrored.of(BuildsScreen.SCREEN)), () -> runs(model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    private static Shown runs(Model model) {
        BuildsScreen screen = model.part(BuildsScreen.PART);
        String title = Messages.get("github.buildsScreen.content.title");
        return switch (screen.workflows()) {
            case Loadable.Loading<?>() -> Shown.loading(title);
            case Loadable.Failed<?>(var failure) -> Shown.dim(title, Placeholders.error(failure));
            case Loadable.Loaded<?> _ ->
                screen.shown()
                        .<Shown>map(workflow -> {
                            RunsDrawn drawn = screen.drawn(model);
                            Option<Build> onLine = drawn.onTimeLine();
                            return new Shown.Area(
                                    AREA,
                                    title(screen, workflow, drawn),
                                    new Runs(workflow.name(), screen.filter()),
                                    new BuildsScreenBody(
                                            defaultLine(model, screen, workflow),
                                            drawn,
                                            onLine,
                                            BuildsScopes.failed(model, onLine)));
                        })
                        .getOrElse(() -> Shown.dim(title, Messages.get("github.buildsScreen.noWorkflows")));
        };
    }

    /**
     * The title of the content: the workflow, which runs, and how many.
     */
    private static String title(BuildsScreen screen, RepoWorkflow workflow, RunsDrawn drawn) {
        String which = switch (screen.filter()) {
            case EVERY_BRANCH -> Messages.get("github.buildsScreen.content.every");
            case DEFAULT_BRANCH ->
                screen.defaultName().map(name -> name.value()).getOrElse("");
            case MINE -> screen.mine().map(mine -> "◆ " + mine.local().value()).getOrElse("");
        };
        return Messages.get("github.buildsScreen.content.of", workflow.name(), which, String.valueOf(drawn.of()));
    }

    /**
     * How the default branch did with the workflow, whatever the filter: how its newest build that is done ended,
     * since when its last builds failed, and how many of those done passed; or why they could not be read (ADR 212).
     */
    static BuildsScreenBody.DefaultLine defaultLine(Model model, BuildsScreen screen, RepoWorkflow workflow) {
        List<Build> done = screen.defaultBuilds()
                .filter(build -> build.workflow().equals(workflow.name()))
                .filter(build -> build.outcome() != Build.Outcome.RUNNING);
        List<Build> failing = done.takeWhile(build -> build.outcome() == Build.Outcome.FAILED);
        Option<String> unread = screen.onDefault().builds() instanceof Loadable.Failed<List<Build>>(var failure)
                ? Option.of(Placeholders.error(failure))
                : Option.none();
        return new BuildsScreenBody.DefaultLine(
                screen.defaultName().map(name -> name.value()).getOrElse(""),
                done.count(build -> build.outcome() == Build.Outcome.PASSED),
                done.size(),
                done.headOption().map(Build::outcome),
                failing.lastOption().map(oldest -> Duration.between(oldest.createdAt(), model.now())),
                unread);
    }

    /**
     * The runs of a workflow, as a filter keeps them, as an area keeps where it is scrolled to for them.
     *
     * @param workflow the workflow
     * @param filter   which of its runs
     */
    record Runs(String workflow, BuildsScreen.Filter filter) implements ScrolledContent.Other {}
}
