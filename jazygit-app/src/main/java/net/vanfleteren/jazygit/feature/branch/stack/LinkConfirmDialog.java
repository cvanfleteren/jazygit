package net.vanfleteren.jazygit.feature.branch.stack;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Asks before linking branches as a stack: which bases change on GitHub first, and which branches do not have all the
 * commits of the one below (ADR 73). Enter links them, Escape cancels; when a branch is so, {@code r} links them and
 * rebases the stack once GitHub lists it (ADR 74).
 */
final class LinkConfirmDialog implements PopupView {

    static final String ID = "stack-confirm";

    private static final int WIDTH = 60;
    // The blank line and the hint below the message, plus the top and bottom border.
    private static final int EXTRA_HEIGHT = 2 + 2;

    private final Consumer<Msg> dispatch;

    LinkConfirmDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(StackPopups.Confirm.class).map(confirm -> {
            String text = StackDialogs.changes(confirm);
            boolean diverged = !confirm.diverged().isEmpty();
            return DialogFrame.withHint(
                            StackDialogs.CONFIRM.title().apply(confirm),
                            WIDTH,
                            diverged ? "dialog.makeStack.hintRebase" : "dialog.hint.confirmCancel",
                            List.of(DialogFrame.wrapped(text, WIDTH, Integer.MAX_VALUE)))
                    .id(ID)
                    .focusable()
                    .length(DialogFrame.wrappedHeight(text, WIDTH, Integer.MAX_VALUE) + EXTRA_HEIGHT)
                    .onKeyEvent(event -> keys(event, diverged));
        });
    }

    private EventResult keys(KeyEvent event, boolean diverged) {
        if (event.code() == KeyCode.ENTER) {
            dispatch.accept(new Msg.PopupConfirmed(StackPopups.Confirm.class));
        } else if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new Msg.PopupDismissed(StackPopups.Confirm.class));
        } else if (diverged && event.isChar('r')) {
            dispatch.accept(new StackMsg.LinkAndRebaseChosen());
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }
}
