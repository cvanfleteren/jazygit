package net.vanfleteren.jazygit.features.github.builds.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.features.github.builds.RunsDrawn;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScopes;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreenMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.DrawContext;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.KeyContext;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The runs of a workflow on the builds screen (ADR 212): the line of the default branch, then the summary, the chart,
 * the jobs and the run on the time line, drawn as wide as the area.
 *
 * @param onDefault  how the default branch did with the workflow
 * @param runs       the runs the filter keeps, as they are drawn
 * @param onTimeLine the run whose jobs show on one time line, which its keys act on
 * @param failed     what failed in it, for the keys other features add to a failed build (ADR 161)
 */
record BuildsScreenBody(DefaultLine onDefault, RunsDrawn runs, Option<Build> onTimeLine, Option<FailedBuild> failed)
        implements Body {

    // The line of the default branch and a blank line come before the overview.
    private static final int ABOVE = 2;

    @Override
    public Drawn draw(DrawContext context) {
        return Drawn.plain(List.of(line(context.styles()), Line.from(""))
                .appendAll(BuildsText.overview(runs, context.zone(), context.styles(), context.width())));
    }

    @Override
    public boolean drawnToWidth() {
        return true;
    }

    @Override
    public Option<Msg> key(KeyEvent event, KeyContext context) {
        return context.keymap()
                .handle(BuildsScopes.CONTENT, event, onTimeLine, new Keymap.Hosted<>(Scopes.FAILED_BUILD, failed));
    }

    /**
     * A click on a run in the chart highlights it in the runs pane, and shows its jobs on the time line.
     */
    @Override
    public Option<Msg> clicked(int width, int line, int column) {
        return BuildsText.buildAt(runs, width, line - ABOVE, column, false).map(BuildsScreenMsg.RunClicked::new);
    }

    /**
     * The line of the default branch: since when it fails, and how many of its builds of the workflow passed.
     */
    Line line(Theme.SpanStyles styles) {
        if (onDefault.unread().isDefined()) {
            return Line.from(Span.styled(
                    Messages.get(
                            "github.buildsScreen.default.unread",
                            onDefault.unread().get()),
                    styles.of(Theme.Classes.ERROR)));
        }
        if (onDefault.done() == 0) {
            return Line.from(Span.styled(
                    Messages.get("github.buildsScreen.default.none", onDefault.branch()),
                    styles.of(Theme.Classes.DIM)));
        }
        String passed = Messages.get(
                "github.buildsScreen.default.passed",
                String.valueOf(onDefault.passed()),
                String.valueOf(onDefault.done()));
        Span branch = Span.styled(onDefault.branch() + " ", styles.of(Theme.Classes.BOLD));
        return onDefault
                .failingFor()
                .map(failing -> Line.from(
                        branch,
                        Span.styled(
                                Messages.get("github.buildsScreen.default.failing", TimeText.age(failing)),
                                styles.of(Theme.Classes.CHECKS_FAILED)),
                        Span.styled(" · " + passed, styles.of(Theme.Classes.DIM))))
                .getOrElse(() -> Line.from(
                        branch, last(onDefault.last(), styles), Span.styled(passed, styles.of(Theme.Classes.DIM))));
    }

    /**
     * How the newest build that is done ended, when it did not fail: passed, or cancelled, which is not taken for a
     * build that passed.
     */
    private static Span last(Option<Build.Outcome> last, Theme.SpanStyles styles) {
        return last.contains(Build.Outcome.CANCELLED)
                ? Span.styled("⊘ ", styles.of(Theme.Classes.DIM))
                : Span.styled("✓ ", styles.of(Theme.Classes.CHECKS_PASSED));
    }

    /**
     * How the default branch did with a workflow.
     *
     * @param branch     its name
     * @param passed     how many of its builds that are done passed
     * @param done       how many of its builds among the commits read are done
     * @param last       how the newest of those ended, if there is one
     * @param failingFor how long its builds have failed, since the oldest of the last that failed; none when the
     *                   newest did not fail
     * @param unread     why its builds could not be read, when they could not
     */
    record DefaultLine(
            String branch,
            int passed,
            int done,
            Option<Build.Outcome> last,
            Option<Duration> failingFor,
            Option<String> unread) {}
}
