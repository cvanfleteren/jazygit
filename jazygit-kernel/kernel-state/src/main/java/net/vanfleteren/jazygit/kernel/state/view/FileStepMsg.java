package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * The user asked to move to the file {@code step} of the one highlighted in the files pane, from the content panel
 * (ADR 117). The highlight of the files pane is view state, so the update leaves the model as it is and asks the app
 * to move it (ADR 207).
 */
public record FileStepMsg(FileStepMsg.Step step) implements Msg {

    /**
     * Which way the highlight moves.
     */
    public enum Step {
        NEXT,
        PREVIOUS
    }

    @Override
    public Update.Next apply(Model model) {
        return Update.Next.of(model).asking(new ViewIntent.StepFile(step));
    }
}
