package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.state.view.LineNumberMsg;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * Tests on what an area of the content panel knows of itself, worked out as a value (#539): a frame fits it, and the
 * keys turn it into another, without a TamboUI element.
 */
class AreaStateTest {

    private static final String DIFF = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +three
             four
            """;
    // Two hunks, with a marker for the lines between them.
    private static final String GAPPED = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,2 +1,2 @@
             one
            -two
            +zwei
            @@ -20,2 +20,2 @@
             twenty
            -twenty-one
            +einundzwanzig
            """;
    private static final Option<Rect> NOWHERE = Option.none();
    private static final Vector<AreaTool> TOOLS = AreaTool.standard();
    private static final ContentPanel PANEL = new ContentPanel(Theme.SpanStyles.NONE, () -> false, focus -> {});

    @Test
    void aFrameFitsTheAreaToWhatItShows() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));

        assertTrue(fitted.after().cursor().isDefined());
        assertEquals(shown(model).content(), fitted.after().content());
        assertTrue(fitted.after().focused());
        assertTrue(fitted.drawing().lines() > 0);
        assertTrue(
                fitted.drawing().title().startsWith(shown(model).title()),
                fitted.drawing().title());
    }

    @Test
    void jMovesTheCursorAStopDownAndTellsTheModelInOneMessage() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));
        DiffCursor before = fitted.after().cursor().get();

        Reaction pressed = AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar('j'), TOOLS);

        assertTrue(pressed.handled());
        DiffCursor after = pressed.state().cursor().get();
        assertEquals(before.stop() + 1, after.stop());
        ViewMsg.ContentScrolled sent = (ViewMsg.ContentScrolled) pressed.sent().single();
        assertEquals(Option.of(after.anchor()), sent.to().cursor());
        assertEquals(Option.of(sent.to()), pressed.state().sent().map(AreaState.Sent::told));
    }

    @Test
    void aFrameDrawnBeforeTheModelHasAMoveShowsWhereItTookTheCursor() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));
        Reaction pressed = AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar('j'), TOOLS);
        int moved = pressed.state().cursor().get().stop();

        AreaFit.Fitted meanwhile = fit(model, pressed.state());
        assertEquals(moved, meanwhile.after().cursor().get().stop());
        assertTrue(meanwhile.after().sent().isDefined());

        Model told = pressed.sent()
                .foldLeft(model, (at, msg) -> Update.update(at, msg).model());
        AreaFit.Fitted known = fit(told, meanwhile.after());
        assertEquals(moved, known.after().cursor().get().stop());
        assertTrue(known.after().sent().isEmpty(), "the model has it now");
    }

    @Test
    void aKeyInTheAreaWithTheFocusClearsWhatTheTitleSaid() {
        Model model = commit();
        AreaState said = fit(model, initial(model)).after().withNotice(Option.of("search wrapped"));

        Reaction pressed = AreaKeys.beforeLeaving(said, new Viewport(0, 10, 80, 20), KeyEvent.ofChar('x'), TOOLS);

        assertFalse(pressed.handled());
        assertEquals(Option.none(), pressed.state().notice());
    }

    @Test
    void colonOpensTheLineToTypeALineNumberIn() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));

        Reaction pressed = AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar(':'), TOOLS);

        assertEquals(Option.of(""), pressed.state().line().number());
        assertEquals(Vector.<Msg>of(new LineNumberMsg.Opened()), pressed.sent());
    }

    @Test
    void enterInTheSearchLineSendsSubmittedBeforeGoingToTheFirstMatch() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));

        Reaction pressed = AreaSearch.typed(
                fitted.after(), view(fitted), "three", KeyEvent.ofKey(KeyCode.ENTER), (text, event) -> Option.none());

        assertEquals(2, pressed.sent().size(), pressed.sent().toString());
        assertEquals(new ContentSearchMsg.Submitted(), pressed.sent().get(0));
        ViewMsg.ContentScrolled moved = (ViewMsg.ContentScrolled) pressed.sent().get(1);
        assertTrue(moved.to().found().isDefined());
    }

    @Test
    void enterInTheLineNumberLineSendsClosedBeforeGoingToTheLine() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));

        Reaction pressed = AreaLines.typed(
                fitted.after(), view(fitted), "3", KeyEvent.ofKey(KeyCode.ENTER), (text, event) -> Option.none());

        assertEquals(2, pressed.sent().size(), pressed.sent().toString());
        assertEquals(new LineNumberMsg.Closed(), pressed.sent().get(0));
        ViewMsg.ContentScrolled moved = (ViewMsg.ContentScrolled) pressed.sent().get(1);
        assertEquals(
                Option.of(new Anchor.OnLine("a.txt", ReviewThread.Side.RIGHT, 3)),
                moved.to().cursor());
    }

    @Test
    void aToolAddedToTheAreaGetsItsKeysBeforeTheCursorMoves() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));
        Msg said = new ContentSearchMsg.Cleared();
        AreaTool third = new AreaTool() {
            @Override
            public Option<StyledElement<?>> line(AreaState state) {
                return Option.none();
            }

            @Override
            public Option<Reaction> typed(AreaState state, Viewport view, KeyEvent event) {
                return Option.none();
            }

            @Override
            public Option<Reaction> key(AreaState state, Viewport view, KeyEvent event) {
                return Option.when(
                        event.isChar('j'), () -> Reaction.handled(state, view).sending(said));
            }

            @Override
            public Option<Reaction> pasted(AreaState state, Viewport view, PasteEvent event) {
                return Option.none();
            }
        };

        Reaction pressed =
                AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar('j'), TOOLS.append(third));

        assertEquals(Vector.of(said), pressed.sent());
        assertEquals(fitted.after().cursor(), pressed.state().cursor());
    }

    @Test
    void theLineOfTheFirstToolWithOneOpenTakesTheKeysAndThePaste() {
        Model model = commit();
        AreaState both = fit(model, initial(model))
                .after()
                .withSearch(new AreaState.Search(Option.of("th"), Option.none()))
                .withLine(new AreaState.GoToLine(Option.of("1"), Option.none()));
        Viewport view = new Viewport(0, 10, 80, 20);

        Reaction typed = AreaKeys.beforeLeaving(both, view, KeyEvent.ofChar('r'), TOOLS);
        Option<Reaction> pasted = AreaTool.first(TOOLS, tool -> tool.pasted(both, view, new PasteEvent("ee")));

        assertEquals(Vector.<Msg>of(new ContentSearchMsg.Edited("thr")), typed.sent());
        assertEquals(Option.of(Vector.<Msg>of(new ContentSearchMsg.Edited("thee"))), pasted.map(Reaction::sent));
    }

    @Test
    void aDigitPastedInTheLineNumberLineIsTypedThere() {
        Model model = commit();
        AreaState numbering =
                fit(model, initial(model)).after().withLine(new AreaState.GoToLine(Option.of("1"), Option.none()));

        Option<Reaction> pasted =
                AreaTool.first(TOOLS, tool -> tool.pasted(numbering, new Viewport(0, 10, 80, 20), new PasteEvent("2")));
        Option<Reaction> refused =
                AreaTool.first(TOOLS, tool -> tool.pasted(numbering, new Viewport(0, 10, 80, 20), new PasteEvent("x")));

        assertEquals(
                Option.of(Option.of("12")),
                pasted.map(reaction -> reaction.state().line().number()));
        assertEquals(Option.of(Vector.<Msg>empty()), refused.map(Reaction::sent));
        assertEquals(Option.of(numbering), refused.map(Reaction::state));
    }

    @Test
    void aPasteTheOpenLineRefusesGoesToNoToolBehindIt() {
        Model model = commit();
        AreaState numbering =
                fit(model, initial(model)).after().withLine(new AreaState.GoToLine(Option.of("1"), Option.none()));
        Msg stolen = new ContentSearchMsg.Edited("abc");
        AreaTool behind = new AreaTool() {
            @Override
            public Option<StyledElement<?>> line(AreaState state) {
                return Option.none();
            }

            @Override
            public Option<Reaction> typed(AreaState state, Viewport view, KeyEvent event) {
                return Option.none();
            }

            @Override
            public Option<Reaction> key(AreaState state, Viewport view, KeyEvent event) {
                return Option.none();
            }

            @Override
            public Option<Reaction> pasted(AreaState state, Viewport view, PasteEvent event) {
                return Option.of(Reaction.handled(state, view).sending(stolen));
            }
        };

        Option<Reaction> pasted = AreaTool.first(
                TOOLS.append(behind),
                tool -> tool.pasted(numbering, new Viewport(0, 10, 80, 20), new PasteEvent("abc")));

        assertEquals(Option.of(Vector.<Msg>empty()), pasted.map(Reaction::sent));
    }

    @Test
    void theWheelScrollsAnAreaWithoutACursorThreeLinesClampedToItsLines() {
        AreaState plain = AreaState.initial(new AreaId("content"));

        Reaction down = AreaMouse.mouse(plain, new Viewport(0, 30, 80, 10), MouseEvent.scrollDown(5, 5), NOWHERE);
        assertEquals(3, down.view().top());
        assertEquals(3, ((ViewMsg.ContentScrolled) down.sent().single()).to().row());

        Reaction atBottom = AreaMouse.mouse(plain, new Viewport(19, 30, 80, 10), MouseEvent.scrollDown(5, 5), NOWHERE);
        assertEquals(20, atBottom.view().top());

        Reaction up = AreaMouse.mouse(plain, new Viewport(1, 30, 80, 10), MouseEvent.scrollUp(5, 5), NOWHERE);
        assertEquals(0, up.view().top());
        assertEquals(0, ((ViewMsg.ContentScrolled) up.sent().single()).to().row());
    }

    @Test
    void theWheelMovesTheCursorOfARichDiffThreeLines() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));
        DiffCursor expected = fitted.after().cursor().get().scrolled(3);

        Reaction down = AreaMouse.mouse(fitted.after(), view(fitted), MouseEvent.scrollDown(5, 5), NOWHERE);

        assertEquals(Option.of(expected), down.state().cursor());
        ViewMsg.ContentScrolled sent = (ViewMsg.ContentScrolled) down.sent().single();
        assertEquals(Option.of(expected.anchor()), sent.to().cursor());
    }

    @Test
    void aClickOnAMarkerMovesTheCursorThereBeforeShowingItsLines() {
        Model model = commit(GAPPED);
        AreaFit.Fitted fitted = fit(model, initial(model));
        int gap = fitted.after()
                .styled()
                .get()
                .stops()
                .find(stop -> stop.mark().anchor() instanceof Anchor.Gap)
                .get()
                .row();
        Viewport view = view(fitted);

        // The text starts a line below the top of the area, under its border.
        Reaction clicked = AreaMouse.mouse(
                fitted.after(),
                view,
                MouseEvent.press(MouseButton.LEFT, 5, gap - view.top() + 1),
                Option.of(new Rect(0, 0, 82, 22)));

        assertTrue(clicked.state().cursor().get().anchor() instanceof Anchor.Gap);
        assertEquals(2, clicked.sent().size(), clicked.sent().toString());
        assertInstanceOf(ViewMsg.ContentScrolled.class, clicked.sent().get(0));
        assertFalse(
                clicked.sent().get(1) instanceof ViewMsg.ContentScrolled,
                clicked.sent().toString());
    }

    private static AreaFit.Fitted fit(Model model, AreaState before) {
        return AreaFit.fit(
                before,
                new AreaFit.Showing(model, true, shown(model), PANEL::bodyDrawn, Theme.SpanStyles.NONE),
                new Viewport(0, 0, 80, 20));
    }

    private static Viewport view(AreaFit.Fitted fitted) {
        return new Viewport(fitted.drawing().row(), fitted.drawing().lines(), 80, 20);
    }

    private static AreaState initial(Model model) {
        return AreaState.initial(shown(model).area().id());
    }

    private static Shown.Area shown(Model model) {
        return ContentView.of(model, CommitsPanel.PANE).areas().head();
    }

    private static Model commit() {
        return commit(DIFF);
    }

    private static Model commit(String diff) {
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        return Update.update(
                        Update.update(model, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"), model.preferences().diffOptions(), DiffText.of(diff)))
                .model();
    }
}
