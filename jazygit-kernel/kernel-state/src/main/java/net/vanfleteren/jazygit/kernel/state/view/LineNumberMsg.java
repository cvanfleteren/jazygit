package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * What the user does with the line to type a line number in, to go to that line of what the content panel shows
 * (ADR 140). Where the area goes is told with {@link ViewMsg.ContentScrolled}, as the area works that out.
 */
public sealed interface LineNumberMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return Update.Next.of(model.withLineNumber(next(model.lineNumber())));
    }

    /**
     * The text of the line after this, {@code current} before; none while it is closed.
     */
    Option<String> next(Option<String> current);

    /**
     * The user opened the line above the area, empty.
     */
    record Opened() implements LineNumberMsg {

        @Override
        public Option<String> next(Option<String> current) {
            return Option.some("");
        }
    }

    /**
     * The user changed the text of the open line to {@code text}.
     */
    record Edited(String text) implements LineNumberMsg {

        @Override
        public Option<String> next(Option<String> current) {
            return current.map(ignored -> text);
        }
    }

    /**
     * The user closed the line, going to the line typed or nowhere.
     */
    record Closed() implements LineNumberMsg {

        @Override
        public Option<String> next(Option<String> current) {
            return Option.none();
        }
    }
}
