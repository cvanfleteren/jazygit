package net.vanfleteren.jazygit.content;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.ui.content.ShownDiff;
import net.vanfleteren.jazygit.ui.i18n.Messages;

/**
 * What the sources of the content panel share in how they show a diff: its titles and notes. The messages in place of
 * what a source shows are {@code Shown}'s, which the sources of the features share.
 */
final class Shows {

    private static final DiffText NO_CHANGES = DiffText.of("");

    private Shows() {}

    /**
     * {@code diff}, with a line saying it is truncated when it was cut off.
     */
    static ShownDiff shown(DiffText diff) {
        return new ShownDiff(diff, Option.when(diff.truncated(), () -> Messages.get("content.git.diff.truncated")));
    }

    /**
     * What a commit changed as it is shown: when changes to whitespace only are left out and nothing else
     * changed, a line that says so.
     */
    static ShownDiff shownChanges(DiffText changes, DiffOptions options) {
        return changes.isBlank() && options.ignoreWhitespace()
                ? new ShownDiff(NO_CHANGES, Option.of(Messages.get("content.onlyWhitespace")))
                : shown(changes);
    }

    /**
     * {@code title}, saying how much context is shown when it is not git's own (ADR 76), and that changes to
     * whitespace only are left out when they are.
     */
    static String titled(String title, DiffOptions options) {
        String withContext = withContext(title, options.context());
        return options.ignoreWhitespace() ? Messages.get("content.panel.ignoringWhitespace", withContext) : withContext;
    }

    /**
     * {@code title}, saying how much context is shown when it is not git's own (ADR 76).
     */
    private static String withContext(String title, DiffContext context) {
        if (context.equals(DiffContext.DEFAULT)) {
            return title;
        }
        return context.wholeFile()
                ? Messages.get("content.panel.wholeFile", title)
                : Messages.get("content.panel.context", title, context.lines());
    }
}
