package net.vanfleteren.jazygit.state.compose;

import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Writing the text of a {@link ComposePopup} in the editor (ADR 115).
 */
public sealed interface ComposeCmd extends Cmd {

    /**
     * Opens {@code text} in git's editor, as {@code file}, with the terminal handed over to it, and reports how it was
     * saved.
     */
    record OpenInEditor(EditorFile file, String text) implements ComposeCmd {

        @Override
        public Msg run(Env env) {
            return env.terminal()
                    .handOver(() -> env.git().editor().editText(file.name(), text))
                    .mapLeft(Failure::of)
                    .fold(this::failed, edited -> new ComposeMsg.EditorClosed(file, edited._1, edited._2));
        }

        @Override
        public Msg failed(Failure failure) {
            return new ComposeMsg.EditorFailed(file, failure);
        }
    }
}
