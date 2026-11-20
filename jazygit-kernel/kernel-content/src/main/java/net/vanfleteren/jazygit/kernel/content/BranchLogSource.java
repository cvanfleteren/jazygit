package net.vanfleteren.jazygit.kernel.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.view.NoLog;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.commits.AheadView;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The log of the branch highlighted in the branches pane, when nothing else is shown for it: the last of the sources
 * of that pane.
 */
record BranchLogSource() implements ContentSource {

    /**
     * The log of the branch highlighted in the branches pane.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.LOG, BranchesPanel.PANE);

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.when(pane == Mirrored.BRANCHES, () -> branchLog(model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    private static Shown branchLog(Model model) {
        return model.repoView()
                .preview()
                .branchLog()
                .map(Preview::shown)
                .map(log -> branchLog(model, log))
                .getOrElse(() -> model.repoView()
                        .branches()
                        .noLog()
                        .map(noLog -> Shown.dim(Messages.get("content.panel.log.title"), noLogText(noLog)))
                        .getOrElse(() -> Shown.loading(Messages.get("content.panel.log.title"))));
    }

    /**
     * The log of the branch {@code log} is of, titled with how far the branch is ahead; the one shown before stays
     * while it is read.
     */
    private static Shown branchLog(Model model, Preview<BranchRef, List<Commit>> log) {
        String title = Messages.get("content.panel.log.titleFor", log.key().name());
        AheadView ahead = new AheadView(AheadUpdate.branch(model, log.key()), AheadUpdate.distance(model, log.key()));
        return Shown.whenLoaded(
                ahead.title(title),
                log.value(),
                commits ->
                        new Shown.Area(AREA, ahead.title(title), new Log(commits), new BranchLogBody(commits, ahead)));
    }

    /**
     * Why there is no log to show, {@code noLog}, as it is said.
     */
    private static String noLogText(NoLog noLog) {
        return switch (noLog) {
            case NoLog.Unfetched(var pullRequest) ->
                Messages.get("content.unfetched", String.valueOf(pullRequest.value()));
            case NoLog.Detached(var at) -> Messages.get("content.detached", at.shortSha());
        };
    }

    /**
     * The log of a branch, {@code commits}, as an area keeps where it is scrolled to for it.
     */
    record Log(List<Commit> commits) implements ScrolledContent.Other {}
}
