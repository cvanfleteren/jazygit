package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface DiffOptionsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DiffOptionsUpdate.update(model, this);
    }

    /**
     * The user asked to show changes to whitespace only, or to leave them out.
     */
    record WhitespaceToggled() implements DiffOptionsMsg {}

    /**
     * The user asked to draw the diffs of the working tree, of a commit and of a stash in the other style.
     */
    record StyleToggled() implements DiffOptionsMsg {}

    /**
     * The user asked for the rich view to highlight the words that changed in a changed line, or only the line.
     */
    record WordsToggled() implements DiffOptionsMsg {}
}
