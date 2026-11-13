package net.vanfleteren.jazygit.feature.files.step;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.operation.Notice;
import net.vanfleteren.jazygit.state.view.FileStepMsg.Step;

/**
 * The files pane had no file {@code step} of the one highlighted, which is its last or its first (ADR 117): the user
 * is told so.
 */
public record FileStepEnded(Step step) implements Msg {

    @Override
    public Update.Next apply(Model model) {
        return Update.Next.of(model.inform(step == Step.NEXT ? new Notice.NoNextFile() : new Notice.NoPreviousFile()));
    }
}
