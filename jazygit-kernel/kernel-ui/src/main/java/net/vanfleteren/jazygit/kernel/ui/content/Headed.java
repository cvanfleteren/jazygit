package net.vanfleteren.jazygit.kernel.ui.content;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * {@code body} with {@code heading} above it, which another source than the one that shows it put there (ADR 187).
 * Past its lines, which are part of the lead of the body, it is the body.
 */
public record Headed(Heading heading, Body body) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        return body.draw(context).headed(heading.drawn(context.styles()));
    }

    @Override
    public boolean drawnToWidth() {
        return body.drawnToWidth();
    }

    @Override
    public Option<ShownChanges> changesShown() {
        return body.changesShown();
    }

    @Override
    public Body withDiff(UnaryOperator<ShownDiff> change) {
        return new Headed(heading, body.withDiff(change));
    }

    @Override
    public Keymap.Scope<DiffCursorAt> cursorKeys() {
        return body.cursorKeys();
    }

    @Override
    public Option<Msg> key(KeyEvent event, KeyContext context) {
        return body.key(event, context);
    }

    @Override
    public Option<DiffStops.HunkAt> markedHunk(Anchor mark) {
        return body.markedHunk(mark);
    }

    @Override
    public Option<Msg> clicked(int width, int line, int column) {
        return line < heading.height() ? Option.none() : body.clicked(width, line - heading.height(), column);
    }
}
