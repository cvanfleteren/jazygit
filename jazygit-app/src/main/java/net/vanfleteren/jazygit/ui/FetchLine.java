package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Freshness;
import net.vanfleteren.jazygit.state.LoadPullRequests;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;

/**
 * The line under everything else saying which commit is copied to cherry-pick, else what is being read in the
 * background: the remote being fetched from, or the pull requests being read from GitHub. On the right it says how
 * fresh what the screen shows is: when the remotes were last fetched from, or the pull requests read, and whether
 * that failed since. It is always there, blank while there is nothing to say, so the panes above it do not move.
 */
public final class FetchLine {

    /**
     * Rows of the line.
     */
    public static final int HEIGHT = 1;

    private static final String SEPARATOR = " · ";

    private FetchLine() {}

    public static StyledElement<?> render(Model model) {
        TextElement left = model.repoView()
                .cherryPick()
                .map(commit -> text(Messages.get("status.cherryPick", commit.shortSha())))
                .getOrElse(() -> background(model));
        String fresh = freshness(model);
        return fresh.isEmpty()
                ? left
                : row(
                        left.fill(),
                        text(fresh)
                                .addClass(Theme.Classes.DIM)
                                .length(Span.raw(fresh).width()));
    }

    private static TextElement background(Model model) {
        return text(model.jobs()
                        .fetching()
                        .flatMap(Fetching::remote)
                        .map(remote -> Messages.get("status.fetching", remote))
                        .orElse(Option.when(
                                model.gitHub().reading().contains(new LoadPullRequests()),
                                () -> Messages.get("status.pullRequests")))
                        .getOrElse(""))
                .addClass(Theme.Classes.DIM);
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
}
