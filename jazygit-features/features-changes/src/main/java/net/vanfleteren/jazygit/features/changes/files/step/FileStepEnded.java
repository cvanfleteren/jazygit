package net.vanfleteren.jazygit.features.changes.files.step;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg.Step;

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
