package net.vanfleteren.jazygit.content;

import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.DrawContext;
import net.vanfleteren.jazygit.ui.content.Drawn;
import net.vanfleteren.jazygit.ui.content.ShownChanges;
import net.vanfleteren.jazygit.ui.content.ShownDiff;

/**
 * A commit and what it changed.
 */
record CommitBody(Commit commit, ShownChanges changes) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        return ContentText.commitDrawn(commit, changes.diff(), changes.look(), context.styles(), context.width());
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
        return new CommitBody(commit, changes.withDiff(change));
    }
}
