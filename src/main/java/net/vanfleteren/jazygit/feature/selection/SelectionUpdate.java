package net.vanfleteren.jazygit.feature.selection;

import net.vanfleteren.jazygit.feature.selection.SelectionMsg.FilesSelected;

import net.vanfleteren.jazygit.feature.selection.SelectionMsg.BranchSelected;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg.CommitSelected;

import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.List;
import java.util.Optional;

/**
 * The panes' highlights, and the data loaded for them.
 */
public final class SelectionUpdate {

    private SelectionUpdate() {
    }

    public static Next update(Model model, SelectionMsg msg) {
        return switch (msg) {
            case BranchSelected(String branch) -> branchSelected(model, branch);
            case CommitSelected(String sha) -> commitSelected(model, sha);
            case FilesSelected(List<FileEntry> files) -> filesSelected(model, files);
        };
    }

    private static Next branchSelected(Model model, String branch) {
        if (model.branchLog().map(BranchLog::branch).filter(branch::equals).isPresent()) {
            return Next.of(model);
        }
        return Next.of(model.withBranchLog(Optional.of(BranchLog.loading(branch, model.branchLog()))),
                new LoadBranchLog(branch));
    }

    private static Next commitSelected(Model model, String sha) {
        if (model.commitDetail().map(CommitDetail::sha).filter(sha::equals).isPresent()) {
            return Next.of(model);
        }
        return Next.of(model.withCommitDetail(Optional.of(CommitDetail.loading(sha, model.commitDetail()))),
                new LoadCommitDetail(sha));
    }

    private static Next filesSelected(Model model, List<FileEntry> files) {
        if (model.fileDiff().map(FileDiff::files).filter(files::equals).isPresent()) {
            return Next.of(model);
        }
        return Next.of(model.withFileDiff(Optional.of(FileDiff.loading(files, model.fileDiff()))),
                new LoadFileDiff(files));
    }
}
