package net.vanfleteren.jazygit.kernel.ui;

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
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.preferences.LeftPanes;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * Top-left panel showing the repository name and the current branch, and what the repository is in the middle
 * of, under the names of the screens. It is display-only and does not take part in focus cycling.
 */
public final class StatusPanel {

    /**
     * Rows of the panel, borders included.
     */
    public static final int HEIGHT = LeftPanes.STATUS_ROWS;

    private static final String SEPARATOR = " · ";
    private static final String TITLE_SEPARATOR = " │ ";

    private StatusPanel() {}

    public static Panel render(Model model) {
        return render(model, Theme.SpanStyles.NONE, Screens.standard(), screen -> {});
    }

    /**
     * @param styles        the styles of the parts of the title
     * @param screens       the screens the title names (ADR 168)
     * @param screenClicked called when the name of a screen in the title is clicked, with that screen
     */
    public static Panel render(Model model, Theme.SpanStyles styles, Screens screens, Consumer<Screen> screenClicked) {
        List<Name> names = names(model, screens);
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
                .title(title(model.screen(), names, styles))
                .length(HEIGHT);
        return panel.onMouseEvent(event -> titleClick(panel.renderedArea(), event, names, screenClicked));
    }

    /**
     * The names of {@code screens} in the title, as {@code model} has them, in their order.
     */
    static List<Name> names(Model model, Screens screens) {
        return screens.all()
                .filter(spec -> spec.screen().shown(model))
                .map(spec -> new Name(
                        spec.screen(), Messages.get(spec.nameKey()), spec.mark().apply(model)));
    }

    /**
     * Shows the screen whose name in the title of the panel, drawn in {@code area} as {@code names}, is clicked. The
     * title starts after the corner of the border.
     */
    private static EventResult titleClick(
            Rect area, MouseEvent event, List<Name> names, Consumer<Screen> screenClicked) {
        return Option.when(
                        event.kind() == MouseEventKind.PRESS && event.isLeftButton() && event.y() == area.top(),
                        event.x() - area.left() - 1)
                .flatMap(column -> screenAt(column, names))
                .map(clicked -> {
                    screenClicked.accept(clicked);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * The screen whose name in the title of {@code names} is {@code column} cells from the start of the title, if
     * any; the separators between them are none, and what follows a name is part of it.
     */
    static Option<Screen> screenAt(int column, List<Name> names) {
        int separator = Span.raw(TITLE_SEPARATOR).width();
        // Where the name of each screen starts: after the names and separators before it.
        List<Integer> starts = names.init().scanLeft(0, (start, name) -> start + name.width() + separator);
        return names.zip(starts)
                .find(name -> column >= name._2 && column < name._2 + name._1.width())
                .map(name -> name._1.screen());
    }

    /**
     * The title of the panel: the names of the screens, the one shown in bold and underlined, the others dimmed,
     * as the lists in the title of the branches pane; with what follows the name of a screen in its own style, as
     * how many pull requests of the repository were opened and not seen yet (ADR 29).
     */
    static Line title(Screen shown, List<Name> names, Theme.SpanStyles styles) {
        return Line.from(names.map(name -> name.mark()
                        .map(mark -> mark.cssClass()
                                .map(cssClass -> Span.styled(mark.text(), styles.of(cssClass)))
                                .getOrElse(() -> Span.raw(mark.text())))
                        .toList()
                        .prepend(
                                name.screen().equals(shown)
                                        ? Span.raw(name.name()).bold().underlined()
                                        : Span.raw(name.name()).dim()))
                .intersperse(List.of(Span.raw(TITLE_SEPARATOR)))
                .flatMap(spans -> spans)
                .toJavaList());
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

    /**
     * The name of a screen in the title.
     *
     * @param screen the screen
     * @param name   its name
     * @param mark   what follows it, if anything
     */
    record Name(Screen screen, String name, Option<LoadableList.Seg> mark) {

        /**
         * How many cells it takes, with what follows it.
         */
        int width() {
            return Span.raw(name).width()
                    + mark.map(seg -> Span.raw(seg.text()).width()).getOrElse(0);
        }
    }
}
