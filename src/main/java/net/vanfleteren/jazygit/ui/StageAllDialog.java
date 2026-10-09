package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Popup asking whether to stage all files, because a commit needs something staged. Enter confirms,
 * Escape cancels. Whether it is open lives in the {@link Model}.
 */
public final class StageAllDialog {

    public static final String ID = "stage-all";

    private static final int WIDTH = 50;

    private final ListElement<?> list = list()
            .id(ID)
            .focusable()
            .highlightSymbol("")
            .elements(text("Nothing is staged. Stage all files?"), text("(enter) confirm   (esc) cancel").dim())
            .onKeyEvent(this::handleKey);
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StageAllDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks whether to stage all files.
     */
    public Optional<Element> render(Model model) {
        return Optional.of(model).filter(Model::stageAllPrompt)
                .map(m -> dialog("Commit", list)
                        .rounded()
                        .width(WIDTH)
                        .padding(1)
                        .onCancel(() -> dispatch.accept(new Msg.StageAllCancelled())));
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new Msg.StageAllCancelled());
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            dispatch.accept(new Msg.StageAllConfirmed());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
