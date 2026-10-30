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
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BorderDragTest {

    // Panes from column 0, 100 wide, so a column is a percent; the left column is their first 30 columns, its
    // border at columns 29 and 30.
    private static final Rect PANES = new Rect(0, 0, 100, 100);
    private static final Rect LEFT = new Rect(0, 0, 30, 100);
    // The panes of the left column, 100 rows tall so a row is a percent, under a status of 3 rows: the files take 25,
    // the branches 20, the commits 30 and the stash the rest. The bottom border of the files is at row 27.
    private static final List<Rect> LEFT_PANES =
            List.of(new Rect(0, 3, 30, 25), new Rect(0, 28, 30, 20), new Rect(0, 48, 30, 30), new Rect(0, 78, 30, 22));

    private final Model model = TestModels.loaded();
    private final java.util.List<Msg> sent = new ArrayList<>();
    private final BorderDrag drag = new BorderDrag(sent::add);

    @BeforeEach
    void drawFrame() {
        drag.shown(drawnAt(PANES), drawnAt(LEFT), LEFT_PANES.map(BorderDragTest::drawnAt));
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
    void draggingTheBottomBorderOfAPaneResizesItAndTheReleaseLetsGo() {
        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 27), model));

        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.drag(MouseButton.LEFT, 10, 32), model));
        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.release(MouseButton.LEFT, 12, 22), model));

        assertEquals(
                List.of(
                        new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 30),
                        new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 20),
                        new LayoutMsg.PaneSettled(Screen.REPOSITORY)),
                sent());
    }

    @Test
    void eachPaneButTheLastHasABottomBorderToDrag() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 77), model);
        drag.handle(MouseEvent.release(MouseButton.LEFT, 10, 87), model);

        assertEquals(
                List.of(
                        new LayoutMsg.PaneResized(Screen.REPOSITORY, 2, 85),
                        new LayoutMsg.PaneSettled(Screen.REPOSITORY)),
                sent());
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 99), model));
    }

    @Test
    void aClickOnTheBorderOfAPaneTallerThanItsShareResizesNothing() {
        // The stash shrunk to its rows, the commits fill what it leaves: rows 48 to 87, not their 30 percent.
        drag.shown(
                drawnAt(PANES),
                drawnAt(LEFT),
                List.of(
                                new Rect(0, 3, 30, 25),
                                new Rect(0, 28, 30, 20),
                                new Rect(0, 48, 30, 40),
                                new Rect(0, 88, 30, 12))
                        .map(BorderDragTest::drawnAt));

        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 87), model));
        drag.handle(MouseEvent.drag(MouseButton.LEFT, 12, 87), model);
        assertEquals(EventResult.HANDLED, drag.handle(MouseEvent.release(MouseButton.LEFT, 12, 87), model));

        assertEquals(List.of(new LayoutMsg.PaneSettled(Screen.REPOSITORY)), sent());
    }

    @Test
    void aBorderOfAPaneTallerThanItsShareDraggedBackKeepsItsShare() {
        // The commits fill rows 48 to 87, as in the click above; a slip of a row and back.
        drag.shown(
                drawnAt(PANES),
                drawnAt(LEFT),
                List.of(
                                new Rect(0, 3, 30, 25),
                                new Rect(0, 28, 30, 20),
                                new Rect(0, 48, 30, 40),
                                new Rect(0, 88, 30, 12))
                        .map(BorderDragTest::drawnAt));

        drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 87), model);
        drag.handle(MouseEvent.drag(MouseButton.LEFT, 10, 86), shown());
        drag.handle(MouseEvent.release(MouseButton.LEFT, 10, 87), shown());

        assertEquals(Settings.DEFAULT, shown().preferences().settings());
        assertEquals(Option.none(), shown().preferences().draggedHeights());
    }

    @Test
    void aBorderDraggedBackInAColumnOfFewerThan100RowsKeepsItsPercent() {
        // 37 rows, of which the files take rows 3 to 11: 9 rows, which are 24 percent, not the 25 kept.
        drag.shown(
                drawnAt(new Rect(0, 0, 100, 37)),
                drawnAt(new Rect(0, 0, 30, 37)),
                List.of(new Rect(0, 3, 30, 9), new Rect(0, 12, 30, 8), new Rect(0, 20, 30, 11), new Rect(0, 31, 30, 6))
                        .map(BorderDragTest::drawnAt));

        drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 11), model);
        drag.handle(MouseEvent.drag(MouseButton.LEFT, 10, 12), shown());
        drag.handle(MouseEvent.release(MouseButton.LEFT, 10, 11), shown());

        assertEquals(Settings.DEFAULT, shown().preferences().settings());
    }

    @Test
    void aBorderDraggedBackToTheRowItWasPressedOnGoesBack() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 27), model);
        drag.handle(MouseEvent.drag(MouseButton.LEFT, 10, 32), model);
        Model dragged = Update.update(model, new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 30))
                .model();

        drag.handle(MouseEvent.release(MouseButton.LEFT, 10, 27), dragged);

        assertEquals(
                List.of(
                        new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 30),
                        new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 25),
                        new LayoutMsg.PaneSettled(Screen.REPOSITORY)),
                sent());
    }

    @Test
    void theTopBorderOfAPaneWithItsTitleIsNoBorderToDrag() {
        assertEquals(EventResult.UNHANDLED, drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 28), model));
        assertEquals(List.of(), sent());
    }

    @Test
    void aHeightThatDoesNotChangeIsNotSent() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 10, 27), model);

        drag.handle(MouseEvent.drag(MouseButton.LEFT, 10, 27), model);

        assertEquals(List.of(), sent());
    }

    @Test
    void whereTheBordersMeetTheBorderOfTheLeftColumnIsDragged() {
        drag.handle(MouseEvent.press(MouseButton.LEFT, 29, 27), model);
        drag.handle(MouseEvent.release(MouseButton.LEFT, 49, 37), model);

        assertEquals(
                List.of(new LayoutMsg.Resized(Screen.REPOSITORY, 50), new LayoutMsg.Settled(Screen.REPOSITORY)),
                sent());
    }

    @Test
    void beforeAFrameIsDrawnThereIsNoBorder() {
        BorderDrag undrawn = new BorderDrag(sent::add);

        assertEquals(EventResult.UNHANDLED, undrawn.handle(MouseEvent.press(MouseButton.LEFT, 29, 5), model));
        assertEquals(EventResult.UNHANDLED, undrawn.handle(MouseEvent.press(MouseButton.LEFT, 10, 27), model));
    }

    private List<Msg> sent() {
        return List.ofAll(sent);
    }

    /**
     * The model as the messages sent so far leave it.
     */
    private Model shown() {
        return sent().foldLeft(model, (shown, msg) -> Update.update(shown, msg).model());
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
