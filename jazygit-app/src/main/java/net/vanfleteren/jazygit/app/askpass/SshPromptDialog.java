package net.vanfleteren.jazygit.app.askpass;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.popup.SshPromptPopup;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * The popup in which ssh asks the user for a secret, such as the passphrase of a key (ADR 150): what ssh asks, as it
 * asks it, and a line that shows a dot for each character typed.
 */
public final class SshPromptDialog implements PopupView {

    static final String ID = "ssh-prompt";
    private static final int WIDTH = 70;
    // The most lines of ssh's prompt that are shown.
    private static final int PROMPT_LINES = 4;

    private final PopupAsking asking;

    private SshPromptDialog(PopupAsking asking) {
        this.asking = asking;
    }

    /**
     * The popup, asking through {@code asking}; opened from anywhere, it gives the focus back to where it was.
     */
    public static PopupSlot slot(PopupAsking asking) {
        return PopupSlot.returningToFocus(new SshPromptDialog(asking));
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(SshPromptPopup.class)
                .map(open -> DialogFrame.withHint(
                        Messages.get("app.sshPrompt.title"),
                        WIDTH,
                        "dialog.hint.confirmCancel",
                        List.of(
                                DialogFrame.wrapped(open.prompt().strip(), WIDTH, PROMPT_LINES),
                                text(""),
                                new SecretInput(asking, ID))));
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    /**
     * The line a secret is typed in, such as the passphrase of a key (ADR 150): it shows a {@code •} for each
     * character, and keeps the characters in the {@link PopupAsking}, never in a {@code String} or the model. Enter
     * answers, Escape cancels, Backspace takes the last character away; a key with Ctrl is left to the app.
     */
    static final class SecretInput extends StyledElement<SecretInput> {

        private static final String DOT = "•";

        private final PopupAsking asking;

        SecretInput(PopupAsking asking, String id) {
            this.asking = asking;
            id(id);
            focusable();
            length(1);
        }

        @Override
        public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
            if (!focused || event.hasCtrl()) {
                return EventResult.UNHANDLED;
            }
            if (event.isPressOrRepeat()) {
                if (event.isConfirm()) {
                    asking.confirm();
                } else if (event.isCancel()) {
                    asking.cancel();
                } else if (event.isKey(KeyCode.BACKSPACE)) {
                    asking.erase();
                } else if (event.isKey(KeyCode.CHAR) && !event.hasAlt()) {
                    asking.type(event.character());
                }
            }
            // The popup keeps every other key, as it asks for nothing else.
            return EventResult.HANDLED;
        }

        /**
         * Takes what a terminal pasted as a paste after all, but for its line breaks.
         */
        @Override
        public EventResult handlePasteEvent(PasteEvent event) {
            event.text().codePoints().filter(c -> c != '\n' && c != '\r').forEach(asking::type);
            return EventResult.HANDLED;
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return new Size(availableWidth, 1);
        }

        @Override
        protected void renderContent(Frame frame, Rect area, RenderContext context) {
            frame.buffer()
                    .setString(
                            area.x(),
                            area.y(),
                            DOT.repeat(Math.min(asking.typed(), area.width())),
                            context.currentStyle());
        }
    }
}
