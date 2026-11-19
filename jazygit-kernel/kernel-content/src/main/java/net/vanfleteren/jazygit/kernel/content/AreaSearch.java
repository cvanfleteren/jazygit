package net.vanfleteren.jazygit.kernel.content;

import static net.vanfleteren.jazygit.kernel.content.Reaction.handled;
import static net.vanfleteren.jazygit.kernel.content.Reaction.scrolled;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.kernel.content.diff.DiffCursor;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Found;
import net.vanfleteren.jazygit.kernel.state.diff.Scrolled;
import net.vanfleteren.jazygit.kernel.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.Halves;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The search of the content panel in an area (ADR 126): its line, the keys that go from match to match, and what the
 * title says of it.
 */
final class AreaSearch {

    // The keys that go to the next and the previous match while a search is kept.
    private static final char NEXT_MATCH = 'n';
    private static final char PREVIOUS_MATCH = 'N';

    private AreaSearch() {}

    /**
     * What {@code event} does while the line of the search is open with {@code text}: the keys that {@code edit} it do,
     * Escape closes it, Enter closes it and goes to the first match from the cursor, or from the top of the screen
     * without one. The other keys do nothing.
     */
    static Reaction typed(
            AreaState state,
            Viewport view,
            String text,
            KeyEvent event,
            BiFunction<String, KeyEvent, Option<String>> edit) {
        if (event.isCancel()) {
            return handled(state, view).sending(new ContentSearchMsg.Cancelled());
        }
        if (event.isConfirm()) {
            Option<Styled> searched = state.styled().map(shown -> shown.searched(Option.when(!text.isEmpty(), text)));
            AreaState submitted = state.withMatch(Option.none()).withStyled(searched);
            return searched.flatMap(Styled::hits)
                    .filter(hits -> !hits.isEmpty())
                    .map(hits -> goTo(submitted, view, hits, hits.forwardFrom(position(submitted, view))))
                    .getOrElse(() -> handled(submitted, view))
                    .preceded(new ContentSearchMsg.Submitted());
        }
        return edit.apply(text, event)
                .map(edited -> handled(state, view).sending(new ContentSearchMsg.Edited(edited)))
                .getOrElse(() -> handled(state, view));
    }

    /**
     * Where {@code n} and {@code N} go while a search is kept, if {@code event} is one of them: from the match gone to,
     * else from the cursor, or the top of the screen without one; nowhere when nothing matches.
     */
    static Option<Reaction> step(AreaState state, Viewport view, KeyEvent event) {
        boolean forward = event.isChar(NEXT_MATCH);
        return state.styled()
                .flatMap(Styled::hits)
                .filter(ignored -> forward || event.isChar(PREVIOUS_MATCH))
                .map(hits -> hits.isEmpty()
                        ? handled(state, view)
                        : goTo(state, view, hits, step(state, view, hits, forward)));
    }

    /**
     * Which of {@code hits} {@code n}, or {@code N} unless {@code forward}, goes to: from the match gone to, else from
     * the cursor, or the top of the screen without one.
     */
    private static SearchHits.Step step(AreaState state, Viewport view, SearchHits hits, boolean forward) {
        return state.search()
                .current()
                .map(at -> hits.from(at, forward))
                .getOrElse(() ->
                        forward ? hits.forwardFrom(position(state, view)) : hits.backwardFrom(position(state, view)));
    }

    /**
     * Where {@code n}, or {@code N} unless {@code forward}, takes the cursor {@code on}, as the message that takes it
     * there, which the keys of the area send and the help runs: none when nothing matches, or a range is open and the
     * match is outside its hunk. The wrap is not said in the title then.
     */
    static Option<Msg> toMatch(AreaState state, Viewport view, DiffCursor on, boolean forward) {
        return state.styled()
                .flatMap(Styled::hits)
                .filter(hits -> !hits.isEmpty())
                .flatMap(hits -> {
                    SearchHits.Step step = step(state, view, hits, forward);
                    SearchHits.Hit hit = hits.get(step.index());
                    return on.toLine(hit.row(), sideOf(state, hit))
                            .map(to -> new ViewMsg.ContentScrolled(
                                    state.area(),
                                    new Scrolled(
                                            state.content(),
                                            to.row(),
                                            shownColumn(state, view, hits, hit),
                                            Option.of(to.anchor()),
                                            to.markAnchor(),
                                            Option.of(new Found.InStop(
                                                    to.anchor(), hits.nth(step.index(), to.stopStart()))))));
                });
    }

    /**
     * The line a search goes on from when no match is current: the first of the stop of the cursor, else the first on
     * the screen.
     */
    private static int position(AreaState state, Viewport view) {
        return state.cursor().map(DiffCursor::stopStart).getOrElse(view.top());
    }

    /**
     * Goes to the match {@code step} says of {@code hits}: the cursor to its stop, or the view scrolled to it, and
     * sideways until it shows, telling the model in one message. While a range is open, a match outside its hunk is
     * refused, and the title says so.
     */
    private static Reaction goTo(AreaState state, Viewport view, SearchHits hits, SearchHits.Step step) {
        SearchHits.Hit hit = hits.get(step.index());
        AreaState gone = state.withNotice(Option.when(step.wrapped(), () -> Messages.get("content.search.wrapped")))
                .withMatch(Option.of(step.index()))
                .withColumn(shownColumn(state, view, hits, hit));
        if (state.cursor().isDefined()) {
            Option<DiffCursor> to = state.cursor().get().toLine(hit.row(), sideOf(state, hit));
            if (to.isEmpty()) {
                return handled(state.withNotice(Option.of(Messages.get("content.search.outsideRange"))), view);
            }
            DiffCursor on = to.get();
            return scrolled(
                    gone.withCursor(to),
                    view.at(on.row()),
                    Option.of(new Found.InStop(on.anchor(), hits.nth(step.index(), on.stopStart()))));
        }
        int top = view.top();
        int height = Math.max(1, view.height());
        int row = hit.row() >= top && hit.row() < top + height ? top : Math.max(0, hit.row() - height / 2);
        return scrolled(gone, view.at(row), Option.of(new Found.OnLine(hit.row(), hits.nth(step.index(), hit.row()))));
    }

    /**
     * How far the area is scrolled sideways to show {@code hit} of {@code hits}: as far as it is, when it shows the
     * match already; else so the match starts at its left edge, or ends at its right one.
     */
    private static int shownColumn(AreaState state, Viewport view, SearchHits hits, SearchHits.Hit hit) {
        int column = state.columns().column();
        int furthest = Math.max(0, state.columns().furthest());
        return state.styled()
                .map(shown -> {
                    String text = shown.lines().get(hit.row()).rawContent();
                    int from = shown.from(hit);
                    int until = Math.min(text.length(), from + hits.query().length());
                    int start = Span.raw(text.substring(0, from)).width();
                    int end = Span.raw(text.substring(0, until)).width();
                    // In the split view, the code of each half scrolls from where it starts, in its own width, which
                    // a cut-off marker at either end narrows (ADR 139).
                    Option<Halves> halves = shown.split().halvesOf(hit.row());
                    if (halves.isDefined()) {
                        Halves cut = halves.get();
                        boolean left = start < cut.rule();
                        int code = left ? cut.leftCode() : cut.rightCode();
                        int room = Math.max(1, (left ? cut.leftShown() : cut.rightShown()) - 2);
                        int at = start - code;
                        int to = end - code;
                        int moved = at < column ? at : to > column + room ? to - room : column;
                        return Math.clamp(moved, 0, furthest);
                    }
                    int width = Math.max(1, view.width());
                    // The gutter of a line of a version shown blamed stays where it is; its code scrolls after it
                    // (ADR 147).
                    int kept = shown.split().keptAt(hit.row());
                    if (kept == Drawn.PINNED) {
                        return column;
                    }
                    int shifted = start - kept < column ? start - kept : end > column + width ? end - width : column;
                    return Math.clamp(shifted, 0, furthest);
                })
                .getOrElse(column);
    }

    /**
     * The side of the split view {@code hit} is in (ADR 139); none in a row that is not split.
     */
    private static Option<ReviewThread.Side> sideOf(AreaState state, SearchHits.Hit hit) {
        return state.styled()
                .flatMap(shown -> shown.split().halvesOf(hit.row()).map(cut -> {
                    String text = shown.lines().get(hit.row()).rawContent();
                    return Span.raw(text.substring(0, shown.from(hit))).width() < cut.rule()
                            ? ReviewThread.Side.LEFT
                            : ReviewThread.Side.RIGHT;
                }));
    }

    /**
     * What the title of the area says of the search after what it says already: the query and which of the matches is
     * current, else how many there are, or that there are none; and what the area's notice says.
     */
    static String title(AreaState state, Option<SearchHits> hits) {
        return hits.map(found -> found.isEmpty()
                                ? Messages.get("content.search.none", found.query())
                                : state.search()
                                        .current()
                                        .map(at -> Messages.get(
                                                "content.search.current", found.query(), at + 1, found.size()))
                                        .getOrElse(() ->
                                                Messages.get("content.search.count", found.query(), found.size())))
                        .getOrElse("")
                + state.notice()
                        .map(says -> Messages.get("content.search.notice", says))
                        .getOrElse("");
    }
}
