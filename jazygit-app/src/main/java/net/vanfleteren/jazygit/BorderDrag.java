package net.vanfleteren.jazygit;

import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.layout.LayoutMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.ui.LeftBorder;
import net.vanfleteren.jazygit.ui.PaneBorder;

/**
 * Drags the border between the left column and the content panel, or the bottom border of a pane of the left column,
 * as the last frame drew them: a left press on it starts, unless a popup is open, and the release lets go. A press
 * lets go too, for a release the terminal did not report. The press and the release are handled, so the press does
 * not move the focus and the release draws the width or the height kept.
 *
 * <p>A move while dragging is not handled, though it resizes: TamboUI draws a frame after every event that is
 * handled, and the terminal reports a move for every cell the mouse crosses, which would draw every width in
 * between, lagging behind the mouse. Not handled, the width it dispatches asks for a frame, which is drawn once,
 * after the moves queued before it, with the last width.
 */
final class BorderDrag {

    private final Consumer<Msg> dispatch;
    // The panes and the left column of the last frame, which tell where the border between them is.
    private Option<Element> shownPanes = Option.none();
    private Option<Element> shownLeft = Option.none();
    // The panes of the left column under the status in the last frame, which tell where the borders between them are.
    private List<Element> shownLeftPanes = List.empty();
    // The border the user drags, while they do.
    private Option<Grip> dragging = Option.none();

    BorderDrag(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * Remembers the panes, the left column and the panes in it under the status of the frame that is drawn.
     */
    void shown(Element panes, Element left, List<Element> leftPanes) {
        shownPanes = Option.of(panes);
        shownLeft = Option.of(left);
        shownLeftPanes = leftPanes;
    }

    /**
     * Handles {@code event} on the border of the left column of {@code model}.
     */
    EventResult handle(MouseEvent event, Model model) {
        return dragging.fold(() -> startDrag(event, model), grip -> switch (event.kind()) {
            case DRAG -> {
                resize(grip, event, model);
                yield EventResult.UNHANDLED;
            }
            case RELEASE -> {
                resize(grip, event, model);
                settle(grip);
                yield EventResult.HANDLED;
            }
            // A release the terminal did not report: let go, and the press may start a new drag.
            case PRESS -> {
                settle(grip);
                yield startDrag(event, model);
            }
            default -> EventResult.UNHANDLED;
        });
    }

    /**
     * Starts dragging a border on a left press on it, unless a popup is open. The border of the left column goes
     * before the bottom border of a pane where they meet.
     */
    private EventResult startDrag(MouseEvent event, Model model) {
        if (event.kind() == MouseEventKind.PRESS
                && event.isLeftButton()
                && model.popup().isEmpty()) {
            Screen screen = model.screen();
            dragging = Option.<Grip>when(onBorder(event.x(), event.y()), () -> new LeftEdge(screen))
                    .orElse(() -> PaneBorder.at(leftPaneAreas(), event.x(), event.y())
                            .map(pane -> new PaneBottom(screen, pane)));
            return dragging.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Moves the border {@code grip} to where {@code event} is, if that changes the width or the height.
     */
    private void resize(Grip grip, MouseEvent event, Model model) {
        switch (grip) {
            case LeftEdge(Screen screen) ->
                leftWidth(event.x())
                        .filter(width -> width != model.preferences().leftWidth(screen))
                        .forEach(width -> dispatch.accept(new LayoutMsg.Resized(screen, width)));
            case PaneBottom(Screen screen, int pane) ->
                shownLeft
                        .flatMap(left -> Option.of(left.renderedArea()))
                        .flatMap(left -> PaneBorder.height(left, leftPaneAreas(), pane, event.y()))
                        .filter(height -> !model.preferences()
                                .leftHeights(screen)
                                .drop(pane)
                                .headOption()
                                .contains(height))
                        .forEach(height -> dispatch.accept(new LayoutMsg.PaneResized(screen, pane, height)));
        }
    }

    /**
     * Lets go of the border {@code grip}.
     */
    private void settle(Grip grip) {
        dispatch.accept(
                switch (grip) {
                    case LeftEdge(Screen screen) -> new LayoutMsg.Settled(screen);
                    case PaneBottom(Screen screen, int pane) -> new LayoutMsg.PaneSettled(screen);
                });
        dragging = Option.none();
    }

    /**
     * Where the panes of the left column under the status are, as the last frame drew them.
     */
    private List<Rect> leftPaneAreas() {
        return shownLeftPanes.flatMap(pane -> Option.of(pane.renderedArea()));
    }

    /**
     * Whether {@code (x, y)} is on the border between the left column and the content panel, as the last frame drew
     * them.
     */
    private boolean onBorder(int x, int y) {
        return shownPanes
                .flatMap(panes -> Option.of(panes.renderedArea()))
                .exists(panes -> shownLeft
                        .flatMap(left -> Option.of(left.renderedArea()))
                        .exists(left -> LeftBorder.contains(panes, left, x, y)));
    }

    /**
     * How wide the left column is, in percent of the width of the panes the last frame drew, with its right edge at
     * column {@code x}.
     */
    private Option<Integer> leftWidth(int x) {
        return shownPanes
                .flatMap(panes -> Option.of(panes.renderedArea()))
                .flatMap(panes -> LeftBorder.width(panes, x));
    }

    /**
     * A border the user drags.
     */
    private sealed interface Grip {}

    /**
     * The border between the left column of {@code screen} and the content panel.
     */
    private record LeftEdge(Screen screen) implements Grip {}

    /**
     * The bottom border of pane {@code pane} of the left column of {@code screen}, counted from the first under the
     * status.
     */
    private record PaneBottom(Screen screen, int pane) implements Grip {}
}
