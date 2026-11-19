package net.vanfleteren.jazygit.kernel.ui.content;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * What an area of the content panel shows, as it is styled: each kind of content draws itself, and says what sets it
 * apart from the others, so the content panel names none of them. An interface, not a sealed one, so a group of
 * features can add a kind of content of its own.
 *
 * <p>A body is a value, a record of values: two bodies that show the same are {@code equals}, as the content panel
 * draws a body anew, and searches it again, only when it changes ({@code AppRules}).
 */
public interface Body {

    /**
     * This body drawn as {@code context} says.
     */
    Drawn draw(DrawContext context);

    /**
     * Whether this is drawn for the width of its area, so drawn anew when that changes.
     */
    default boolean drawnToWidth() {
        return false;
    }

    /**
     * The diff this shows and how it is drawn, if it shows one.
     */
    default Option<ShownChanges> changesShown() {
        return Option.none();
    }

    /**
     * This, with the diff it shows, if it shows one, changed by {@code change}.
     */
    default Body withDiff(UnaryOperator<ShownDiff> change) {
        return this;
    }

    /**
     * The keys of the cursor on this body: those of a diff, unless it is one to comment on (ADR 60).
     */
    default Keymap.Scope<DiffCursorAt> cursorKeys() {
        return Scopes.DIFF;
    }

    /**
     * What {@code event} does to the area that shows this, past the keys of its cursor, if it has keys of its own, as
     * the builds of a branch show another workflow (ADR 131). None for most bodies, whose keys are their cursor's.
     */
    default Option<Msg> key(KeyEvent event, KeyContext context) {
        return Option.none();
    }

    /**
     * The hunk of the line {@code mark}, found without drawing this: the hunk a range from that line is open in (ADR
     * 83). None unless this has ranges, as only the diff of a pull request's files does.
     */
    default Option<DiffStops.HunkAt> markedHunk(Anchor mark) {
        return Option.none();
    }

    /**
     * What a click on this, drawn {@code width} columns wide, does at column {@code column} of line {@code line}, if
     * it does more than put the cursor there.
     */
    default Option<Msg> clicked(int width, int line, int column) {
        return Option.none();
    }
}
