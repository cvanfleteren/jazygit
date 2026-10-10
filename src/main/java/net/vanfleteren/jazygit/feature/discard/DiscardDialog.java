package net.vanfleteren.jazygit.feature.discard;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking what to discard. An option is chosen with its letter, or with the arrow keys and Enter;
 * Escape cancels. Whether it is open lives in the {@link Model}.
 */
public final class DiscardDialog {

    public static final String ID = "discard";

    private static final int WIDTH = 40;

    /**
     * An option of the popup; no scope means cancel.
     */
    private record Option(char key, String labelKey, Optional<Scope> scope) {

        Msg msg() {
            return scope.<Msg>map(DiscardMsg.Chosen::new).orElseGet(DiscardMsg.Cancelled::new);
        }

        String row() {
            return Messages.get("dialog.discard.row", String.valueOf(key), Messages.get(labelKey));
        }

        boolean enabled(boolean unstagedAvailable) {
            return unstagedAvailable || scope.filter(s -> s == Scope.UNSTAGED).isEmpty();
        }
    }

    private static final List<Option> OPTIONS = List.of(
            new Option('x', "dialog.discard.all", Optional.of(Scope.ALL)),
            new Option('u', "dialog.discard.unstaged", Optional.of(Scope.UNSTAGED)),
            new Option('c', "dialog.discard.cancel", Optional.empty()));

    private final ListElement<?> list = list()
            .id(ID)
            .focusable()
            .highlightSymbol("")
            .onKeyEvent(this::handleKey);
    private final Consumer<Msg> dispatch;
    // What the rows show; null before the first render.
    private Boolean unstagedAvailable;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public DiscardDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks what to discard.
     */
    public Optional<Element> render(Model model) {
        return model.popup(DiscardPopup.class).map(popup -> {
            showOptions(popup.hasUnstaged());
            return dialog(Messages.get("dialog.discard.title"), list)
                    .rounded()
                    .width(WIDTH)
                    .padding(1)
                    .onCancel(() -> choose(OPTIONS.getLast()));
        });
    }

    /**
     * The option for unstaged changes is struck through and dimmed when there are none.
     */
    private void showOptions(boolean available) {
        if (unstagedAvailable == null || unstagedAvailable != available) {
            unstagedAvailable = available;
            list.elements(OPTIONS.stream()
                    .map(option -> option.enabled(available) ? text(option.row()) : text(option.row()).crossedOut().dim())
                    .toArray(StyledElement[]::new));
        }
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            choose(OPTIONS.getLast());
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            choose(OPTIONS.get(Math.clamp(list.selected(), 0, OPTIONS.size() - 1)));
            return EventResult.HANDLED;
        }
        return OPTIONS.stream()
                .filter(option -> event.isChar(option.key()))
                .findFirst()
                .map(option -> {
                    choose(option);
                    return EventResult.HANDLED;
                })
                .orElse(EventResult.UNHANDLED);
    }

    private void choose(Option option) {
        // A disabled option is not actionable.
        if (!option.enabled(Boolean.TRUE.equals(unstagedAvailable))) {
            return;
        }
        list.selected(0);
        dispatch.accept(option.msg());
    }
}
