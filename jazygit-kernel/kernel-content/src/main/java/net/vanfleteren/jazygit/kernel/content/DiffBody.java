package net.vanfleteren.jazygit.kernel.content;

import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.content.diff.ShownRemarks;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffLines;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.DrawContext;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.content.ShownDiff;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * A unified diff: the staged or the unstaged changes of the working tree, or what a commit, a stash or a pull request
 * changed in some of its files, with the review threads of a pull request (ADR 55), whose keys comment on it (ADR 60).
 */
record DiffBody(ShownChanges changes, Option<ShownRemarks> remarks) implements Body {

    /**
     * A diff without review threads.
     */
    DiffBody(ShownChanges changes) {
        this(changes, Option.none());
    }

    @Override
    public Drawn draw(DrawContext context) {
        return ContentText.diffDrawn(
                changes.diff(), changes.look(), remarks, context.asGiven(), context.styles(), context.width());
    }

    @Override
    public boolean drawnToWidth() {
        return changes.drawnToWidth();
    }

    @Override
    public Option<ShownChanges> changesShown() {
        return Option.of(changes);
    }

    @Override
    public Body withDiff(UnaryOperator<ShownDiff> change) {
        return new DiffBody(changes.withDiff(change), remarks);
    }

    @Override
    public Keymap.Scope<DiffCursorAt> cursorKeys() {
        return remarks.isDefined() ? Scopes.PULL_REQUEST_DIFF : Scopes.DIFF;
    }

    /**
     * The hunk of the line {@code mark}, found in the diff without drawing it. Numbered as the rich view numbers them,
     * among the hunks of the diff as its lines left out are shown (ADR 77).
     */
    @Override
    public Option<DiffStops.HunkAt> markedHunk(Anchor mark) {
        return DiffLines.of(changes.diff().folded().diff().diff())
                .lines()
                // A context line by either anchor, as the split view has it a stop on both sides (ADR 139).
                .find(line -> mark instanceof Anchor.OnLine on && line.answers(on))
                .map(DiffLines.Line::hunk);
    }
}
