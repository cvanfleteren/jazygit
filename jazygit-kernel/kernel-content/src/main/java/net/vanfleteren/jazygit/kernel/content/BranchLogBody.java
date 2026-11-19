package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.DrawContext;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;

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
