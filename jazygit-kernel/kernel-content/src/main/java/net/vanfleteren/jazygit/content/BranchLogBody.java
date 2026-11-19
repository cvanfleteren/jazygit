package net.vanfleteren.jazygit.content;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.ui.commits.AheadView;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.DrawContext;
import net.vanfleteren.jazygit.ui.content.Drawn;

/**
 * The log of a branch, as far as {@code ahead} shows it.
 */
record BranchLogBody(List<Commit> commits, AheadView ahead) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        return Drawn.plain(List.ofAll(LogText.logText(commits, context.zone(), ahead, context.styles())
                .lines()));
    }
}
