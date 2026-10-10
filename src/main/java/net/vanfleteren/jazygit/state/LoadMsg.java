package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;

import java.util.List;

/**
 * The outcome of reading from the repository.
 */
public sealed interface LoadMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return LoadUpdate.update(model, this);
    }

    record StatusLoaded(RepoStatus status) implements LoadMsg {
    }

    record BranchesLoaded(List<Branch> branches) implements LoadMsg {
    }

    record CommitsLoaded(List<Commit> commits) implements LoadMsg {
    }

    record BranchLogLoaded(String branch, List<Commit> commits) implements LoadMsg {
    }

    record CommitDetailLoaded(String sha, String changes) implements LoadMsg {
    }

    record FileDiffLoaded(List<FileEntry> files, Diffs diff) implements LoadMsg {

        public FileDiffLoaded {
            files = List.copyOf(files);
        }
    }

    record Failed(Cmd.Load cmd, String message) implements LoadMsg {
    }
}
