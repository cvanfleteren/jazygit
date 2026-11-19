package net.vanfleteren.jazygit.content;

import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.DrawContext;
import net.vanfleteren.jazygit.ui.content.Drawn;
import net.vanfleteren.jazygit.ui.content.ShownChanges;
import net.vanfleteren.jazygit.ui.content.ShownDiff;

/**
 * A stash and what it stashed.
 */
record StashBody(Stash stash, ShownChanges changes) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        return ContentText.stashDrawn(stash, changes.diff(), changes.look(), context.styles(), context.width());
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
        return new StashBody(stash, changes.withDiff(change));
    }
}
