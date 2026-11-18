package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.ContentView.Shown;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.LineNumberMsg;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
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
    private static final AreaKeys.Edits NO_EDITS =
            new AreaKeys.Edits((text, event) -> Option.none(), (text, event) -> Option.none());
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

        Reaction pressed = AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar('j'));

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
        Reaction pressed = AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar('j'));
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

        Reaction pressed = AreaKeys.beforeLeaving(said, new Viewport(0, 10, 80, 20), KeyEvent.ofChar('x'), NO_EDITS);

        assertFalse(pressed.handled());
        assertEquals(Option.none(), pressed.state().notice());
    }

    @Test
    void colonOpensTheLineToTypeALineNumberIn() {
        Model model = commit();
        AreaFit.Fitted fitted = fit(model, initial(model));

        Reaction pressed = AreaKeys.afterLeaving(fitted.after(), view(fitted), KeyEvent.ofChar(':'));

        assertEquals(Option.of(""), pressed.state().line().number());
        assertEquals(Vector.<Msg>of(new LineNumberMsg.Opened()), pressed.sent());
    }

    @Test
    void theViewIsClampedAsTheTextAreaClampsIt() {
        Viewport view = new Viewport(0, 30, 80, 10);

        assertEquals(20, view.at(25).top());
        assertEquals(0, view.by(-3).top());
        assertEquals(9, view.pageDown().top());
        assertEquals(20, view.bottom().top());
        assertEquals(0, new Viewport(0, 5, 80, 10).bottom().top());
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
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        return Update.update(
                        Update.update(model, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"), model.preferences().diffOptions(), DiffText.of(DIFF)))
                .model();
    }
}
