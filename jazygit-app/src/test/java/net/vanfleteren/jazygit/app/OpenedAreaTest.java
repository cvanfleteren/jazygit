package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.PaneId;
import org.junit.jupiter.api.Test;

/**
 * How an area that says what is opened in it takes the focus from its pane and gives it back (ADR 155), apart from
 * the builds that use it, and from TamboUI, which moves the focus.
 */
class OpenedAreaTest {

    private static final AreaId AREA = new AreaId("mine");
    private static final PaneId PANE = new PaneId("pane");
    private static final PaneId OTHER = new PaneId("other");

    private static OpenedArea.Following follow(OpenedArea before, Option<?> now, Option<Focus> focus, boolean drawn) {
        return OpenedArea.follow(before, now, focus, () -> drawn, AREA, PANE);
    }

    private static Option<Focus> on(Focus focus) {
        return Option.of(focus);
    }

    @Test
    void whatIsOpenedTakesTheFocusFromThePaneOnceTheAreaIsDrawn() {
        OpenedArea.Following notYet = follow(OpenedArea.NOTHING, Option.of("a"), on(PANE), false);
        OpenedArea.Following drawn = follow(notYet.next(), Option.of("a"), on(PANE), true);

        assertEquals(new OpenedArea.Following(new OpenedArea(Option.of("a"), true), Option.none()), notYet);
        assertEquals(new OpenedArea.Following(new OpenedArea(Option.of("a"), false), on(AREA)), drawn);
    }

    @Test
    void whatIsOpenedWhileAnotherPaneHasTheFocusLeavesIt() {
        assertEquals(
                new OpenedArea.Following(new OpenedArea(Option.of("a"), false), Option.none()),
                follow(OpenedArea.NOTHING, Option.of("a"), on(OTHER), true));
    }

    @Test
    void whatIsOpenedInPlaceOfAnotherTakesTheFocusAgain() {
        OpenedArea shown = new OpenedArea(Option.of("main"), false);

        assertEquals(
                new OpenedArea.Following(new OpenedArea(Option.of("feature"), false), on(AREA)),
                follow(shown, Option.of("feature"), on(PANE), true));
    }

    @Test
    void theSameStillOpenTakesTheFocusNoMore() {
        OpenedArea shown = new OpenedArea(Option.of("a"), false);

        assertEquals(new OpenedArea.Following(shown, Option.none()), follow(shown, Option.of("a"), on(PANE), true));
    }

    @Test
    void closedWhileTheAreaHasTheFocusGivesItBackToThePane() {
        assertEquals(
                new OpenedArea.Following(OpenedArea.NOTHING, on(PANE)),
                follow(new OpenedArea(Option.of("a"), false), Option.none(), on(AREA), false));
    }

    @Test
    void closedWhileElsewhereHasTheFocusLeavesIt() {
        assertEquals(
                new OpenedArea.Following(OpenedArea.NOTHING, Option.none()),
                follow(new OpenedArea(Option.of("a"), false), Option.none(), on(OTHER), false));
    }

    @Test
    void closedBeforeItWasDrawnWaitsNoMore() {
        assertEquals(
                new OpenedArea.Following(OpenedArea.NOTHING, Option.none()),
                follow(new OpenedArea(Option.of("a"), true), Option.none(), on(PANE), false));
    }
}
