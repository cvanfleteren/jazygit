package net.vanfleteren.jazygit.feature.branch;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.function.Consumer;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Popup asking where to push a branch without an upstream, as {@code <remote> <branch>}; it starts out
 * with the branch of the same name on {@code origin}. Enter pushes, Escape closes the popup. Whether it is
 * open lives in the {@link Model}; the text being typed lives in the input widget.
 */
public final class SetUpstreamDialog {

    public static final String ID = "set-upstream";

    private static final int WIDTH = 50;
    private static final String DEFAULT_REMOTE = "origin";

    private final TextInputState input = new TextInputState();
    private final Consumer<Msg> dispatch;
    // Whether the popup was open at the previous render, so the default is only filled in when it opens.
    private boolean shown;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public SetUpstreamDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The popup, while the model asks for the upstream of a branch.
     */
    public Option<Element> render(Model model) {
        Option<String> branch = model.popup(SetUpstreamPopup.class).map(SetUpstreamPopup::branch);
        if (branch.isEmpty()) {
            shown = false;
            return Option.none();
        }
        branch.filter(b -> !shown).forEach(b -> {
            shown = true;
            input.setText(new RemoteBranch(DEFAULT_REMOTE, b).text());
            input.moveCursorToEnd();
        });
        return Option.of(dialog(Messages.get("dialog.setUpstream.title"),
                        textInput(input)
                                .id(ID)
                                .onSubmit(this::confirm)
                                .onKeyEvent(event -> {
                                    if (event.code() == KeyCode.ESCAPE) {
                                        cancel();
                                        return EventResult.HANDLED;
                                    }
                                    return EventResult.UNHANDLED;
                                }))
                .rounded()
                .width(WIDTH)
                .padding(1)
                .onConfirm(this::confirm)
                .onCancel(this::cancel));
    }

    private void confirm() {
        // What cannot be read as <remote> <branch> keeps the popup open with its text, see PushUpdate.
        dispatch.accept(new PushMsg.UpstreamConfirmed(input.text()));
    }

    private void cancel() {
        input.clear();
        dispatch.accept(new PushMsg.UpstreamCancelled());
    }
}
