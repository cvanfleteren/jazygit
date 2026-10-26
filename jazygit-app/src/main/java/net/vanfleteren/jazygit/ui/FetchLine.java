package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Freshness;
import net.vanfleteren.jazygit.state.Jobs;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;

/**
 * The line under everything else saying which commit is copied to cherry-pick. On the right it says what runs (ADR
 * 33): a spinner and the command that ran longest of those that ran {@link Jobs#SHOWN_AFTER}, with how many more do;
 * then how fresh what the screen shows is: when the remotes were last fetched from, or the pull requests read, and
 * whether that failed since. It is always there, blank while there is nothing to say, so the panes above it do not
 * move. On a narrow screen what runs is cut first.
 */
public final class FetchLine {

    /**
     * Rows of the line.
     */
    public static final int HEIGHT = 1;

    private static final String SEPARATOR = " · ";

    private FetchLine() {}

    /**
     * The line for {@code model} at {@code now}, the time the frame is drawn, on a screen {@code width} columns wide.
     */
    public static StyledElement<?> render(Model model, Instant now, int width) {
        String cherryPick = model.repoView()
                .cherryPick()
                .map(commit -> Messages.get("status.cherryPick", commit.shortSha()))
                .getOrElse("");
        String fresh = freshness(model);
        String freshText = working(model, now).isDefined() && !fresh.isEmpty() ? SEPARATOR + fresh : fresh;
        List<StyledElement<?>> right = working(model, now)
                .map(work -> {
                    String spinner = TimeText.spinnerFrame(now) + " ";
                    String more = work.more() > 0 ? " " + Messages.get("work.more", String.valueOf(work.more())) : "";
                    // Only the label is cut: the cherry-pick, how many more run and how fresh the screen is stay whole.
                    String label = TextWidth.cut(
                                    work.label(),
                                    width
                                            - TextWidth.of(cherryPick)
                                            - TextWidth.of(spinner)
                                            - TextWidth.of(more)
                                            - TextWidth.of(freshText)
                                            - TextWidth.of(SEPARATOR))
                            + more;
                    return List.<StyledElement<?>>of(
                            text(spinner).addClass(Theme.Classes.SPINNER).length(TextWidth.of(spinner)),
                            text(label).addClass(Theme.Classes.DIM).length(TextWidth.of(label)));
                })
                .getOrElse(List.empty())
                .appendAll(Option.when(
                        !freshText.isEmpty(),
                        () -> text(freshText).addClass(Theme.Classes.DIM).length(TextWidth.of(freshText))));
        TextElement left = text(cherryPick);
        return right.isEmpty() ? left : row(right.prepend(left.fill()).toJavaArray(StyledElement[]::new));
    }

    /**
     * Whether the line shows a spinner at {@code now}, or is about to: while a command runs, as it shows once it ran
     * a while, and while what showed lingers, and one frame longer, so that it is drawn gone.
     */
    public static boolean showsSpinner(Model model, Instant now) {
        return model.jobs().running().nonEmpty()
                || model.jobs()
                        .lingering()
                        .exists(lingering -> !now.isAfter(lingering.until().plus(TimeText.SPINNER_FRAME)));
    }

    /**
     * What runs at {@code now}: the command that ran longest of those that show, and how many more show; else what
     * lingers. Nothing while no command shows.
     */
    static Option<Working> working(Model model, Instant now) {
        List<Jobs.Running> shown = model.jobs()
                .running()
                .toList()
                .sortBy(running -> running._1())
                .map(running -> running._2())
                .filter(running -> running.shown(now))
                .sortBy(Jobs.Running::started);
        return shown.headOption()
                .map(first -> new Working(labelText(first.label()), shown.size() - 1))
                .orElse(() -> model.jobs()
                        .lingering()
                        .filter(lingering -> now.isBefore(lingering.until()))
                        .map(lingering -> new Working(labelText(lingering.label()), 0)));
    }

    /**
     * The text of {@code label}, with the labels it is given as their text.
     */
    static String labelText(Label label) {
        return Messages.get(
                label.key(),
                label.args()
                        .map(arg -> arg instanceof Label inner ? labelText(inner) : arg)
                        .toJavaArray());
    }

    /**
     * How fresh what the screen shows is at the model's time: when the remotes were last fetched from on the repository
     * screen, or the pull requests read on theirs, and that the last try failed when it did; blank before anything
     * was tried.
     */
    static String freshness(Model model) {
        boolean pullRequests = model.screen() == Screen.PULL_REQUESTS;
        Freshness freshness =
                pullRequests ? model.prOverview().freshness() : model.jobs().lastFetch();
        String kind = pullRequests ? "read" : "fetch";
        return List.of(
                        freshness
                                .read()
                                .map(at -> Messages.get(
                                        "status." + kind + ".ago",
                                        TimeText.readAgo(Duration.between(at, model.now())))),
                        Option.when(freshness.stale(), () -> Messages.get("status." + kind + ".failed")))
                .flatMap(part -> part)
                .mkString(SEPARATOR);
    }

    /**
     * What runs: the text of the label of one command, and how many more show.
     */
    record Working(String label, int more) {}
}
