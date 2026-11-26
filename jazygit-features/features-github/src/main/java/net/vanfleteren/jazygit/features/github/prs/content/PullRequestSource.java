package net.vanfleteren.jazygit.features.github.prs.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The pull request highlighted in the list of the pull requests screen in use; the one shown before stays while it is
 * read.
 */
public record PullRequestSource() implements ContentSource {

    /**
     * It shows the pull request highlighted in whichever list of the pull requests screen is in use.
     */
    public static final ContentArea AREA = new ContentArea(
            new AreaId("pull-request-preview"),
            model -> model.paneInUse(PrOverview.SCREEN).getOrElse(PrOverview.SCREEN.first()));

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(pane.equals(Mirrored.of(PrOverview.SCREEN)), () -> pullRequest(model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    private static Shown pullRequest(Model model) {
        String title = Messages.get("github.content.panel.pullRequest.title");
        return model.part(PrOverview.PART)
                .preview()
                .map(Preview::shown)
                .map(preview -> pullRequest(title, preview))
                .getOrElse(() -> Shown.dim(title, Messages.get("github.content.noPullRequest")));
    }

    private static Shown pullRequest(String title, Preview<PullRequestSummary, PullRequestPreview> preview) {
        PullRequestSummary pullRequest = preview.key();
        return Shown.whenLoaded(
                title,
                preview.value(),
                read -> new Shown.Area(
                        AREA,
                        Messages.get(
                                "github.content.panel.pullRequest.titleFor",
                                pullRequest.repository().value(),
                                String.valueOf(pullRequest.number().value())),
                        new Read(pullRequest, read),
                        new PullRequestBody(pullRequest, read)));
    }

    /**
     * A pull request, {@code pullRequest}, and what it says and changes, {@code preview}, as an area keeps where it is
     * scrolled to for it.
     */
    public record Read(PullRequestSummary pullRequest, PullRequestPreview preview) implements ScrolledContent.Other {}
}
