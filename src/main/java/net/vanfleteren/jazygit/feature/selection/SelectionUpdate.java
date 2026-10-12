package net.vanfleteren.jazygit.feature.selection;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg.BranchSelected;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg.CommitSelected;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg.FilesSelected;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The panes' highlights, and the data loaded for them.
 */
public final class SelectionUpdate {

    private SelectionUpdate() {
    }

    public static Next update(Model model, SelectionMsg msg) {
        return switch (msg) {
            case BranchSelected(String branch) -> branchSelected(model, branch);
            case CommitSelected(Sha sha) -> commitSelected(model, sha);
            case FilesSelected(List<FileEntry> files) -> filesSelected(model, files);
        };
    }

    private static Next branchSelected(Model model, String branch) {
        if (model.branchLog().map(BranchLog::branch).filter(branch::equals).isDefined()) {
            return Next.of(model);
        }
        return Next.of(model.withBranchLog(Option.of(BranchLog.loading(branch, model.branchLog()))),
                new LoadBranchLog(branch));
    }

    private static Next commitSelected(Model model, Sha sha) {
        if (model.commitDetail().map(CommitDetail::sha).filter(sha::equals).isDefined()) {
            return Next.of(model);
        }
        return Next.of(model.withCommitDetail(Option.of(CommitDetail.loading(sha, model.commitDetail()))),
                new LoadCommitDetail(sha));
    }

    private static Next filesSelected(Model model, List<FileEntry> files) {
        if (model.fileDiff().map(FileDiff::files).filter(files::equals).isDefined()) {
            return Next.of(model);
        }
        return Next.of(model.withFileDiff(Option.of(FileDiff.loading(files, model.fileDiff()))),
                new LoadFileDiff(files));
    }
}
