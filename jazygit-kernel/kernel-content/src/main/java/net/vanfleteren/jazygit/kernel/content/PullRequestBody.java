package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.DrawContext;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;

/**
 * A pull request, and what it says and changes.
 */
record PullRequestBody(PullRequestSummary pullRequest, PullRequestPreview preview) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        return Drawn.plain(List.ofAll(ContentText.pullRequestText(pullRequest, preview, context.styles())
                .lines()));
    }
}
