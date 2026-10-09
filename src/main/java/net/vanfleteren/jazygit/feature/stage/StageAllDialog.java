package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.feature.commit.CommitMsg;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.style.Overflow;
import dev.tamboui.widgets.paragraph.Paragraph;
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

    private static final String MESSAGE = "Nothing is staged. Stage all files?";
    private static final int MESSAGE_HEIGHT = 1;
    // The message, a blank line and the hint, plus the border.
    private static final int HEIGHT = MESSAGE_HEIGHT + 2 + 2;

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
                .map(m -> dialog("Commit",
                        widget(Paragraph.builder().text(MESSAGE).overflow(Overflow.WRAP_WORD).build())
                                .length(MESSAGE_HEIGHT),
                        text(""),
                        text("(enter) confirm   (esc) cancel").dim())
                        .id(ID)
                        .focusable()
                        .rounded()
                        .width(WIDTH)
                        .length(HEIGHT)
                        .padding(1)
                        .onConfirm(() -> dispatch.accept(new CommitMsg.StageAllConfirmed()))
                        .onCancel(() -> dispatch.accept(new CommitMsg.StageAllCancelled())));
    }
}
