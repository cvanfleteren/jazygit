package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Changing how the diffs are read and drawn, from the popup of the diff options or with a key of their own.
 */
public sealed interface DiffOptionsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DiffOptionsUpdate.update(model, this);
    }

    /**
     * The user asked for the popup of the diff options.
     */
    record Requested() implements DiffOptionsMsg {}

    /**
     * The user asked to show changes to whitespace only, or to leave them out.
     */
    record WhitespaceToggled() implements DiffOptionsMsg {}

    /**
     * The user asked to draw the diffs of the working tree, of a commit and of a stash in {@code style}.
     */
    record StyleChosen(DiffStyle style) implements DiffOptionsMsg {}

    /**
     * The user asked to draw the diffs with the old and the new file side by side, or unified again (ADR 139).
     */
    record SplitToggled() implements DiffOptionsMsg {}

    /**
     * The user asked for the rich view to highlight the words that changed in a changed line, or only the line.
     */
    record WordsToggled() implements DiffOptionsMsg {}

    /**
     * The user asked for the rich view to show each removed line beside the added line that replaced it (ADR 83), or
     * the lines of a hunk as git gives them.
     */
    record PairsToggled() implements DiffOptionsMsg {}

    /**
     * The user asked for the diffs to show more lines around a change.
     */
    record ContextWidened() implements DiffOptionsMsg {}

    /**
     * The user asked for the diffs to show fewer lines around a change.
     */
    record ContextNarrowed() implements DiffOptionsMsg {}
}
