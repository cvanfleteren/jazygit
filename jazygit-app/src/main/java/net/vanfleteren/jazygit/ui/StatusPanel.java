package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TextElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;

/**
 * Top-left panel showing the repository name and the current branch, and what the repository is in the middle
 * of, under the names of the screens. It is display-only and does not take part in focus cycling.
 */
public final class StatusPanel {

    /**
     * Rows of the panel, borders included.
     */
    public static final int HEIGHT = 3;

    private static final String SEPARATOR = " · ";
    private static final String TITLE_SEPARATOR = " │ ";

    private StatusPanel() {}

    public static Panel render(Model model) {
        return render(model, Theme.SpanStyles.NONE, screen -> {});
    }

    /**
     * @param styles        the styles of the parts of the title
     * @param screenClicked called when the name of a screen in the title is clicked, with that screen
     */
    public static Panel render(Model model, Theme.SpanStyles styles, Consumer<Screen> screenClicked) {
        int unseen = model.prOverview().unseenCount();
        String branch = switch (model.repo().status()) {
            case Loadable.Loading<RepoStatus>() -> Placeholders.loading();
            case Loadable.Failed<RepoStatus>(Failure failure) -> Placeholders.error(failure);
            // A rebase checks out a detached HEAD, which says nothing the rebase does not.
            case Loadable.Loaded<RepoStatus>(RepoStatus status) ->
                status.inProgress().exists(InProgress.Rebasing.class::isInstance) ? "" : branchLabel(status.head());
        };
        Option<String> unfinished = model.repo()
                .status()
                .toOption()
                .flatMap(status -> unfinishedLabel(
                        status, model.repo().branches().toOption().getOrElse(List.empty())));
        String line = Messages.get("status.line", model.repositoryName(), branch)
                + unfinished
                        .filter(label -> !branch.isEmpty())
                        .map(label -> SEPARATOR)
                        .getOrElse("");
        TextElement repository = text(line);
        Panel panel = panel(unfinished
                        // The row would share its width out evenly, cutting off what is in progress.
                        .<StyledElement<?>>map(label -> row(
                                repository.length(TextWidth.of(line)),
                                text(label).addClass(Theme.Classes.IN_PROGRESS)))
                        .getOrElse(repository))
                .title(title(model.screen(), unseen, styles))
                .length(HEIGHT);
        return panel.onMouseEvent(event -> titleClick(panel.renderedArea(), event, unseen, screenClicked));
    }

    /**
     * Shows the screen whose name in the title of the panel, drawn in {@code area} with {@code unseen} new pull
     * requests, is clicked. The title starts after the corner of the border.
     */
    private static EventResult titleClick(Rect area, MouseEvent event, int unseen, Consumer<Screen> screenClicked) {
        return Option.when(
                        event.kind() == MouseEventKind.PRESS && event.isLeftButton() && event.y() == area.top(),
                        event.x() - area.left() - 1)
                .flatMap(column -> screenAt(column, unseen))
                .map(clicked -> {
                    screenClicked.accept(clicked);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * The screen whose name in the title is {@code column} cells from the start of the title, if any; the
     * separators between them are none.
     */
    static Option<Screen> screenAt(int column) {
        return screenAt(column, 0);
    }

    /**
     * The screen whose name in the title with {@code unseen} new pull requests is {@code column} cells from the
     * start of the title, if any; the count of the new ones is part of the name of the pull requests screen.
     */
    static Option<Screen> screenAt(int column, int unseen) {
        int separator = Span.raw(TITLE_SEPARATOR).width();
        List<Screen> screens = List.of(Screen.values());
        // Where the name of each screen starts: after the names and separators before it.
        List<Integer> starts =
                screens.init().scanLeft(0, (start, screen) -> start + nameWidth(screen, unseen) + separator);
        return screens.zip(starts)
                .find(screen -> column >= screen._2 && column < screen._2 + nameWidth(screen._1, unseen))
                .map(screen -> screen._1);
    }

    /**
     * The title of the panel: the names of the screens, the one shown in bold and underlined, the others dimmed,
     * as the lists in the title of the branches pane.
     */
    static Line title(Screen shown) {
        return title(shown, 0, Theme.SpanStyles.NONE);
    }

    /**
     * The title of the panel, as {@link #title(Screen)}, with how many pull requests of the repository were
     * opened and not seen yet after the name of their screen, in their own style, when there are any (ADR 29).
     */
    static Line title(Screen shown, int unseen, Theme.SpanStyles styles) {
        List<List<Span>> names = List.of(Screen.values())
                .map(screen -> unseenCount(screen, unseen)
                        .map(count -> Span.styled(count, styles.of(Theme.Classes.UNSEEN)))
                        .prepend(
                                screen == shown
                                        ? screenName(screen).bold().underlined()
                                        : screenName(screen).dim()));
        return Line.from(names.intersperse(List.of(Span.raw(TITLE_SEPARATOR)))
                .flatMap(spans -> spans)
                .toJavaList());
    }

    /**
     * How many pull requests were opened and not seen yet, after the name of {@code screen}: only after the one of
     * the pull requests, and only when there are any.
     */
    private static List<String> unseenCount(Screen screen, int unseen) {
        return screen == Screen.PULL_REQUESTS && unseen > 0
                ? List.of(" " + PullRequestsPanel.UNSEEN_MARK + unseen)
                : List.empty();
    }

    private static int nameWidth(Screen screen, int unseen) {
        return screenName(screen).width()
                + unseenCount(screen, unseen)
                        .map(count -> Span.raw(count).width())
                        .sum()
                        .intValue();
    }

    private static Span screenName(Screen screen) {
        return Span.raw(Messages.get(
                switch (screen) {
                    case REPOSITORY -> "screen.repository";
                    case PULL_REQUESTS -> "screen.pullRequests";
                }));
    }

    /**
     * What the repository is in the middle of, with how many files still have conflicts; or just those, when
     * conflicts are left without an operation, as after a stash that did not apply cleanly. Commits are named
     * by the local branch they are the tip of, if any.
     */
    static Option<String> unfinishedLabel(RepoStatus status, List<Branch> branches) {
        Option<String> conflicts =
                Option.when(status.conflicts() > 0, () -> Messages.get("status.conflicts", status.conflicts()));
        return status.inProgress()
                .map(inProgress -> conflicts
                        .map(text -> operationLabel(inProgress, branches) + SEPARATOR + text)
                        .getOrElse(() -> operationLabel(inProgress, branches)))
                .orElse(() -> Option.when(
                        status.conflicts() > 0, () -> Messages.get("status.conflictsOnly", status.conflicts())));
    }

    private static String operationLabel(InProgress inProgress, List<Branch> branches) {
        return switch (inProgress) {
            case InProgress.Merging(Sha theirs) -> Messages.get("status.merging", name(theirs, branches));
            case InProgress.Rebasing(Option<BranchName> branch, Sha onto, int step, int total) ->
                branch.map(name -> Messages.get("status.rebasing", name.value(), step, total, name(onto, branches)))
                        .getOrElse(() -> Messages.get("status.rebasingDetached", step, total, name(onto, branches)));
            case InProgress.CherryPicking(Sha commit) -> Messages.get("status.cherryPicking", name(commit, branches));
            case InProgress.Reverting(Sha commit) -> Messages.get("status.reverting", name(commit, branches));
        };
    }

    /**
     * The local branch {@code commit} is the tip of, or else its short sha.
     */
    private static String name(Sha commit, List<Branch> branches) {
        return branches.find(branch -> branch.tip().equals(commit))
                .map(branch -> branch.name().value())
                .getOrElse(commit::shortSha);
    }

    /**
     * The checked-out branch, or a description of the detached HEAD.
     */
    static String branchLabel(Head head) {
        return switch (head) {
            case Head.OnBranch(BranchName branch, Option<Sha> tip) -> branch.value();
            case Head.Detached(Sha at) -> Messages.get("status.headDetached", at.shortSha());
        };
    }
}
