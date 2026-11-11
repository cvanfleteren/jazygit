package net.vanfleteren.jazygit.feature.branch.stack;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.popup.ConfirmablePopup;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * A popup asking to confirm, as {@code ConfirmDialog} does, with one key more that does it another way: Enter confirms,
 * Escape cancels, and {@code key}, when the popup offers it, sends {@code other}. The hint says which keys there are.
 *
 * @param <P> the popup that makes this dialog show
 */
final class KeyedConfirmDialog<P extends ConfirmablePopup> implements PopupView {

    private static final int WIDTH = 60;
    // The blank line and the hint below the message, plus the top and bottom border.
    private static final int EXTRA_HEIGHT = 2 + 2;

    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;

    KeyedConfirmDialog(Spec<P> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.dispatch = dispatch;
    }

    @Override
    public List<String> ids() {
        return List.of(spec.id());
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(spec.popupType()).map(popup -> {
            String text = spec.message().apply(popup);
            boolean offered = spec.offered().test(popup);
            return DialogFrame.withHint(
                            spec.title().apply(popup),
                            WIDTH,
                            offered ? spec.hintKey() : "dialog.hint.confirmCancel",
                            List.of(DialogFrame.wrapped(text, WIDTH, Integer.MAX_VALUE)))
                    .id(spec.id())
                    .focusable()
                    .length(DialogFrame.wrappedHeight(text, WIDTH, Integer.MAX_VALUE) + EXTRA_HEIGHT)
                    .onKeyEvent(event -> keys(event, offered));
        });
    }

    private EventResult keys(KeyEvent event, boolean offered) {
        if (event.code() == KeyCode.ENTER) {
            dispatch.accept(new PopupMsg.Confirmed(spec.popupType()));
        } else if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new PopupMsg.Dismissed(spec.popupType()));
        } else if (offered && event.isChar(spec.key())) {
            dispatch.accept(spec.other().get());
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the dialog, which takes the focus while it is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param message   the question, from the popup; it is wrapped at the dialog's width
     * @param key       the key that does it the other way
     * @param offered   whether the popup offers {@code key}
     * @param other     the message for {@code key}
     * @param hintKey   the message key of the hint when {@code key} is offered; else Enter confirms and Escape cancels
     */
    record Spec<P extends ConfirmablePopup>(
            String id,
            Class<P> popupType,
            Function<P, String> title,
            Function<P, String> message,
            char key,
            Predicate<P> offered,
            Supplier<Msg> other,
            String hintKey) {}
}
