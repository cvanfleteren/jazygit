package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.layout.LayoutMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.ui.LeftBorder;

/**
 * Drags the border between the left column and the content panel, as the last frame drew them: a left press on it
 * starts, unless a popup is open, and the release lets go. A press lets go too, for a release the terminal did not
 * report. The press and the release are handled, so the press does not move the focus and the release draws the
 * width kept.
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
    // The screen whose left column the user drags the border of, while they do.
    private Option<Screen> dragging = Option.none();

    BorderDrag(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * Remembers the panes and the left column of the frame that is drawn.
     */
    void shown(Element panes, Element left) {
        shownPanes = Option.of(panes);
        shownLeft = Option.of(left);
    }

    /**
     * Handles {@code event} on the border of the left column of {@code model}.
     */
    EventResult handle(MouseEvent event, Model model) {
        return dragging.fold(() -> startDrag(event, model), screen -> switch (event.kind()) {
            case DRAG -> {
                resize(screen, event.x(), model);
                yield EventResult.UNHANDLED;
            }
            case RELEASE -> {
                resize(screen, event.x(), model);
                settle(screen);
                yield EventResult.HANDLED;
            }
            // A release the terminal did not report: let go, and the press may start a new drag.
            case PRESS -> {
                settle(screen);
                yield startDrag(event, model);
            }
            default -> EventResult.UNHANDLED;
        });
    }

    /**
     * Starts dragging the border on a left press on it, unless a popup is open.
     */
    private EventResult startDrag(MouseEvent event, Model model) {
        if (event.kind() == MouseEventKind.PRESS
                && event.isLeftButton()
                && model.popup().isEmpty()
                && onBorder(event.x(), event.y())) {
            dragging = Option.of(model.screen());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Resizes the left column of {@code screen} so its right edge is at column {@code x}, if that changes its width.
     */
    private void resize(Screen screen, int x, Model model) {
        leftWidth(x)
                .filter(width -> width != model.preferences().leftWidth(screen))
                .forEach(width -> dispatch.accept(new LayoutMsg.Resized(screen, width)));
    }

    /**
     * Lets go of the border of {@code screen}.
     */
    private void settle(Screen screen) {
        dispatch.accept(new LayoutMsg.Settled(screen));
        dragging = Option.none();
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
}
