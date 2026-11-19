package net.vanfleteren.jazygit.state;

/**
 * What the terminal the app runs in reports.
 */
public sealed interface TerminalMsg extends Msg {

    /**
     * The terminal got the focus, or lost it, as a terminal that reports it says.
     */
    record Focused(boolean focused) implements TerminalMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.focused(model, focused);
        }
    }
}
