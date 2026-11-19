package net.vanfleteren.jazygit.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.ui.content.ContentArea;
import net.vanfleteren.jazygit.ui.content.ContentSource;
import net.vanfleteren.jazygit.ui.content.Mirrored;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.github.PullRequestsPanel;
import net.vanfleteren.jazygit.ui.i18n.Messages;

/**
 * The pull request highlighted in the list of the pull requests screen in use; the one shown before stays while it is
 * read.
 */
record PullRequestSource() implements ContentSource {

    /**
     * It shows the pull request highlighted in whichever list of the pull requests screen is in use.
     */
    static final ContentArea AREA = new ContentArea(
            AreaId.PULL_REQUEST, model -> model.prOverview().listPane().getOrElse(PullRequestsPanel.FIRST_PANE));

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(pane == Mirrored.PULL_REQUESTS, () -> pullRequest(model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    private static Shown pullRequest(Model model) {
        String title = Messages.get("content.panel.pullRequest.title");
        return model.prOverview()
                .preview()
                .map(Preview::shown)
                .map(preview -> pullRequest(title, preview))
                .getOrElse(() -> Shows.dim(title, Messages.get("content.noPullRequest")));
    }

    private static Shown pullRequest(String title, Preview<PullRequestSummary, PullRequestPreview> preview) {
        PullRequestSummary pullRequest = preview.key();
        return Shows.whenLoaded(
                title,
                preview.value(),
                read -> new Shown.Area(
                        AREA,
                        Messages.get(
                                "content.panel.pullRequest.titleFor",
                                pullRequest.repository(),
                                String.valueOf(pullRequest.number().value())),
                        new Read(pullRequest, read),
                        new PullRequestBody(pullRequest, read)));
    }

    /**
     * A pull request, {@code pullRequest}, and what it says and changes, {@code preview}, as an area keeps where it is
     * scrolled to for it.
     */
    record Read(PullRequestSummary pullRequest, PullRequestPreview preview) implements ScrolledContent.Other {}
}
