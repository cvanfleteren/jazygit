package net.vanfleteren.jazygit.content;

import static net.vanfleteren.jazygit.content.Reaction.handled;
import static net.vanfleteren.jazygit.content.Reaction.moved;
import static net.vanfleteren.jazygit.content.Reaction.scrolled;
import static net.vanfleteren.jazygit.content.Reaction.unhandled;

import dev.tamboui.layout.Rect;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.ContentView.Body;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.github.builds.BuildsViewMsg;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * What the mouse does to an area: a click puts the cursor of a rich diff on the line clicked, or shows what was
 * clicked in the builds; the wheel scrolls. That a click gives the area the focus, even from the panes, is left to
 * {@link ScrollArea}.
 */
final class AreaMouse {

    private static final int WHEEL_LINES = 3;

    private AreaMouse() {}

    /**
     * What {@code event} does to the area, drawn {@code at} when it was drawn.
     */
    static Reaction mouse(AreaState state, Viewport view, MouseEvent event, Option<Rect> at) {
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            return clicked(state, view, event, at);
        }
        if (event.kind() == MouseEventKind.SCROLL_UP) {
            return wheeled(state, view, -WHEEL_LINES);
        }
        if (event.kind() == MouseEventKind.SCROLL_DOWN) {
            return wheeled(state, view, WHEEL_LINES);
        }
        return unhandled(state, view);
    }

    private static Reaction wheeled(AreaState state, Viewport view, int by) {
        return state.cursor()
                .map(on -> moved(state, view, on.scrolled(by)))
                .getOrElse(() -> scrolled(state, view.by(by)));
    }

    /**
     * A click on a tab of the builds of a branch shows its workflow (ADR 131), one on a build in the chart or the list
     * its jobs on one time line (ADR 133); elsewhere it puts the cursor on the line clicked.
     */
    private static Reaction clicked(AreaState state, Viewport view, MouseEvent event, Option<Rect> at) {
        // The text starts a line below the top of the area, under its border.
        int column = state.columns().column();
        Option<Msg> tab = at.filter(drawn -> view.top() + event.y() - drawn.top() - 1 == 0)
                .flatMap(drawn -> state.styled()
                        .flatMap(shown -> shown.body() instanceof Body.Builds(var builds)
                                ? BuildsText.tabAt(builds, shown.width(), column + event.x() - drawn.left() - 1)
                                : Option.none()))
                .map(BuildsViewMsg.TabClicked::new);
        Option<Msg> build = at.flatMap(drawn -> state.styled()
                        .flatMap(shown -> shown.body() instanceof Body.Builds(var builds)
                                ? BuildsText.buildAt(
                                        builds,
                                        shown.width(),
                                        view.top() + event.y() - drawn.top() - 1,
                                        column + event.x() - drawn.left() - 1)
                                : Option.none()))
                .map(BuildsViewMsg.BuildClicked::new);
        return tab.orElse(build)
                .map(msg -> handled(state, view).sending(msg))
                .getOrElse(() -> onLine(state, view, event, at));
    }

    /**
     * The cursor on the line clicked. A click on a marker shows all the lines it leaves out, as Enter on it does (ADR
     * 77), and one of hunks the diff filters hide shows them, or hides them again (ADR 144).
     */
    private static Reaction onLine(AreaState state, Viewport view, MouseEvent event, Option<Rect> at) {
        return state.cursor()
                .flatMap(on -> at.flatMap(drawn -> on.clicked(
                        event.y() - drawn.top() - 1,
                        event.x() - drawn.left() - 1,
                        state.styled().flatMap(shown -> shown.split().divider()))))
                .map(clicked -> {
                    Reaction moved = moved(state, view, clicked);
                    return clicked.anchor() instanceof Anchor.Gap || clicked.anchor() instanceof Anchor.Filtered
                            ? moved.sendingAll(AreaKeys.KEYMAP.handle(
                                    Scopes.DIFF,
                                    KeyEvent.ofKey(KeyCode.ENTER),
                                    Option.of(AreaKeys.at(moved.state(), moved.view(), clicked))))
                            : moved;
                })
                .getOrElse(() -> handled(state, view));
    }
}
