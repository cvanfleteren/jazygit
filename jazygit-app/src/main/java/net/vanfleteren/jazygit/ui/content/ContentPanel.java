package net.vanfleteren.jazygit.ui.content;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.ZoneId;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.ui.commits.AheadView;
import net.vanfleteren.jazygit.ui.content.ContentView.Body;
import net.vanfleteren.jazygit.ui.content.ContentView.Shown;
import net.vanfleteren.jazygit.ui.content.ContentView.ShownDiff;
import net.vanfleteren.jazygit.ui.content.ContentView.Tone;
import net.vanfleteren.jazygit.ui.diff.RichDiff;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The right-hand content panel: it draws what {@link ContentView} says it shows, styling the diffs, the log, the
 * commits, the stashes and the pull requests ({@link ContentText}), and keeps the areas that scroll through them
 * ({@link ScrollArea}).
 */
public final class ContentPanel {

    private final Theme.SpanStyles styles;
    // The scrollable areas for the diffs, the branch log and the commit. Where they are scrolled to lives in the
    // model; the elements are kept between renders as they also lay out the text for the scrollbar. Only used from
    // the render thread.
    private final Map<ContentArea, ScrollArea> areas;

    /**
     * @param styles          how the diffs and the log are colored
     * @param leaveScrollArea called when Escape, or Left at the left edge of an area, is pressed; gives the focus
     *                        back to the pane if an area had it, and says whether it did
     */
    public ContentPanel(Theme.SpanStyles styles, BooleanSupplier leaveScrollArea) {
        this(styles, leaveScrollArea, id -> {});
    }

    /**
     * @param focus called with the id of a focusable area that is clicked, to give it the focus
     */
    public ContentPanel(Theme.SpanStyles styles, BooleanSupplier leaveScrollArea, Consumer<String> focus) {
        this(styles, leaveScrollArea, focus, msg -> {});
    }

    /**
     * @param dispatch receives where the user scrolled an area to, on the render thread
     */
    public ContentPanel(
            Theme.SpanStyles styles, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
        this.styles = styles;
        areas = List.of(ContentArea.values())
                .toLinkedMap(area -> area, area -> new ScrollArea(area, leaveScrollArea, focus, dispatch));
    }

    /**
     * Whether {@code id} is an area of the content panel that can have the focus, rather than a pane.
     */
    public static boolean isArea(String id) {
        return ContentArea.focusable(id).isDefined();
    }

    /**
     * The pane that gets the focus back when Escape, or Left at its left edge, is pressed in the area with id
     * {@code areaId}, if that is an area: the pane whose highlighted row it shows.
     */
    public Option<PaneId> paneOfArea(Model model, String areaId) {
        return ContentArea.focusable(areaId).map(area -> area.pane(model));
    }

    /**
     * The id of the area that shows what the pane {@code paneId} has highlighted, to move the focus into:
     * the staged diff if it shows anything, else the unstaged one, the commit or the log; none while it shows nothing,
     * or only a note, which the focus is not moved into (ADR 117).
     */
    public Option<String> areaOfPane(Model model, String paneId) {
        return shownOf(model, paneId)
                .find(area -> area != ContentArea.FILE_NOTE)
                .map(area -> area.id().value());
    }

    /**
     * The area to give the focus to when the area with id {@code areaId} has it but no longer shows: the area that shows
     * what the same pane has highlighted now, as the unstaged diff of a file in place of the staged one of the file
     * highlighted before, or a note in place of a diff (ADR 117). None while the area still shows, or nothing takes its
     * place.
     */
    public Option<String> replacing(Model model, String areaId) {
        return paneOfArea(model, areaId)
                .filter(pane -> !shownOf(model, pane.value())
                        .exists(area -> area.id().value().equals(areaId)))
                .flatMap(pane -> shownOf(model, pane.value()).headOption())
                .map(area -> area.id().value());
    }

    /**
     * The areas that show what the pane {@code paneId} has highlighted, top to bottom.
     */
    private static List<ContentArea> shownOf(Model model, String paneId) {
        return ContentView.of(model, paneId)
                .areas()
                .map(Shown.Area::area)
                .filter(area -> area.pane(model).value().equals(paneId));
    }

    /**
     * What goes with the pane that has the focus, {@code focusedId}; while a popup has it, what goes with the
     * pane the popup was opened from. That holds also in the frame a popup closes in, as it still has the focus
     * until the focus is given back.
     */
    public StyledElement<?> render(Model model, String focusedId) {
        tabbableWhileIn(focusedId, model);
        return column(element(ContentView.of(model, focusedId), model, focusedId));
    }

    /**
     * How {@code shown} is drawn: a message in a panel, or the areas, where the model has them scrolled to.
     */
    private Element element(Shown shown, Model model, String focusedId) {
        return switch (shown) {
            case Shown.Message(String title, String content, Tone tone) ->
                panel(
                                title,
                                switch (tone) {
                                    case DIM -> text(content).addClass(Theme.Classes.DIM);
                                    case ERROR -> text(content).addClass(Theme.Classes.ERROR);
                                })
                        .fill();
            case Shown.Area area -> area(area.area()).show(model, focusedId, area, this::bodyDrawn, styles);
            case Shown.Stacked(List<Shown.Area> stacked) ->
                column(stacked.<Element>map(area -> element(area, model, focusedId))
                                .toJavaArray(Element[]::new))
                        .fill();
        };
    }

    /**
     * {@code body}, styled, with the stops of the cursor of a rich diff (ADR 60), the lines of {@code asGiven}, the
     * hunk a range is open in, as git gives them (ADR 83), and a version of a file rendered {@code width} columns wide
     * (ADR 103). Only the diff of a pull request's files has ranges.
     */
    private RichDiff.Drawn bodyDrawn(Body body, Option<DiffStops.HunkAt> asGiven, int width) {
        return switch (body) {
            case Body.Diff(ShownDiff diff, DiffLook look, var remarks) ->
                ContentText.diffDrawn(diff, look, remarks, asGiven, styles, width);
            case Body.BranchLog(List<Commit> commits, AheadView ahead) ->
                plain(ContentText.logText(commits, ZoneId.systemDefault(), ahead, styles));
            case Body.CommitChanges(Commit commit, ShownDiff changes, DiffLook look) ->
                ContentText.commitDrawn(commit, changes, look, styles, width);
            case Body.StashChanges(Stash stash, ShownDiff changes, DiffLook look) ->
                ContentText.stashDrawn(stash, changes, look, styles, width);
            case Body.PullRequest(PullRequestSummary pullRequest, PullRequestPreview preview) ->
                plain(ContentText.pullRequestText(pullRequest, preview, styles));
            case Body.Note(String text, Tone tone) ->
                RichDiff.Drawn.plain(List.of(text.split("\n", -1))
                        .map(line -> Line.from(Span.styled(
                                line,
                                styles.of(
                                        switch (tone) {
                                            case DIM -> Theme.Classes.DIM;
                                            case ERROR -> Theme.Classes.ERROR;
                                        })))));
        };
    }

    private static RichDiff.Drawn plain(Text text) {
        return RichDiff.Drawn.plain(List.ofAll(text.lines()));
    }

    /**
     * Lets Tab move between the areas only while one of them, {@code focusedId}, has the focus, or had it
     * before a popup took it: the area then takes the focus back when the popup closes, as from the popup of a review
     * comment (ADR 60).
     */
    private void tabbableWhileIn(String focusedId, Model model) {
        boolean inContent = isArea(focusedId) || model.shownPane().exists(ContentPanel::isArea);
        areas.values().forEach(area -> area.tabbable(inContent));
    }

    private ScrollArea area(ContentArea area) {
        return areas.apply(area);
    }
}
