package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.layout.LayoutMsg;
import net.vanfleteren.jazygit.feature.theme.ThemePopup;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BorderDragTest {

    // Panes from column 0, 100 wide, so a column is a percent; the left column is their first 30 columns, its
    // border at columns 29 and 30.
    private static final Rect PANES = new Rect(0, 0, 100, 20);
    private static final Rect LEFT = new Rect(0, 0, 30, 20);

    private final Model model = TestModels.loaded();
    private final java.util.List<Msg> sent = new ArrayList<>();
    private final BorderDrag drag = new BorderDrag(sent::add);

    @BeforeEach
    void drawFrame() {
        drag.shown(drawnAt(PANES), drawnAt(LEFT));
    }

    @Test
    void aLeftPressOnTheBorderStartsADragAndIsHandled() {
        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model));
        assertEquals(List.of(), sent());
    }

    @Test
    void aPressElsewhereOrWithAnotherButtonOrUnderAPopupIsLeftAlone() {
        Model open = model.openPopup(new ThemePopup(ThemeName.DARK, Option.none()));

        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 5), model));
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.press(MouseButton.RIGHT, 29, 5), model));
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), open));
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.drag(MouseButton.LEFT, 49, 5), model));
        assertEquals(List.of(), sent());
    }

    @Test
    void draggingResizesWithoutBeingHandledAndTheReleaseLetsGo() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model);

        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.drag(MouseButton.LEFT, 39, 5), model));
        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.release(MouseButton.LEFT, 49, 5), model));

        assertEquals(
                List.of(
                        new LayoutMsg.Resized(Screen.REPOSITORY, 40),
                        new LayoutMsg.Resized(Screen.REPOSITORY, 50),
                        new LayoutMsg.Settled(Screen.REPOSITORY)),
                sent());
        // Let go, the next drag does nothing.
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.drag(MouseButton.LEFT, 59, 5), model));
        assertEquals(3, sent.size());
    }

    @Test
    void aWidthThatDoesNotChangeIsNotSent() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model);

        // The right edge at column 29 is the 30 percent the left column already has.
        drag.handle(MouseEvent.drag(MouseButton.LEFT, 29, 5), model);

        assertEquals(List.of(), sent());
    }

    @Test
    void movesAndScrollsWhileDraggingDoNothing() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model);

        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.move(49, 5), model));
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.scrollDown(49, 5), model));

        assertEquals(List.of(), sent());
    }

    @Test
    void aPressWhileDraggingLetsGoAndMayStartAnotherDrag() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model);

        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 30, 5), model));
        assertEquals(List.of(new LayoutMsg.Settled(Screen.REPOSITORY)), sent());

        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 5), model));
        assertEquals(
                List.of(new LayoutMsg.Settled(Screen.REPOSITORY), new LayoutMsg.Settled(Screen.REPOSITORY)), sent());
    }

    @Test
    void theDragKeepsTheScreenItStartedOn() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model);

        drag.handle(MouseEvent.release(MouseButton.LEFT, 49, 5), model.withScreen(Screen.PULL_REQUESTS));

        assertEquals(
                List.of(new LayoutMsg.Resized(Screen.REPOSITORY, 50), new LayoutMsg.Settled(Screen.REPOSITORY)),
                sent());
    }

    @Test
    void beforeAFrameIsDrawnThereIsNoBorder() {
        BorderDrag undrawn = new BorderDrag(sent::add);

        assertEquals(EventResult.UNHANDLED, undrawn.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model));
    }

    private List<Msg> sent() {
        return List.ofAll(sent);
    }

    private static Element drawnAt(Rect area) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect rect, RenderContext context) {}

            @Override
            public Size preferredSize(int width, int height, RenderContext context) {
                return Size.UNKNOWN;
            }

            @Override
            public Rect renderedArea() {
                return area;
            }
        };
    }
}
