package net.vanfleteren.jazygit.feature.github.review;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

/**
 * Looking at the review threads of the diff of a pull request's files, from its cursor: collapsing a thread or a
 * comment, or opening it again (ADR 62), and opening a comment on GitHub (ADR 85). Neither changes anything on GitHub.
 */
public sealed interface ThreadMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ThreadUpdate.update(model, this);
    }

    /**
     * The user asked to open or close the thread of the cursor {@code at}.
     */
    record ThreadToggled(DiffCursorAt at) implements ThreadMsg {}

    /**
     * The user asked to open the pull request in the browser, on the comment of the cursor {@code at}, if it is on one
     * (ADR 85).
     */
    record OpenAsked(DiffCursorAt at) implements ThreadMsg {}
}
