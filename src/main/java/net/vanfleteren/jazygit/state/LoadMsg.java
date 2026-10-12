package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;

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

    record StashesLoaded(List<Stash> stashes) implements LoadMsg {
    }

    record CommitsLoaded(List<Commit> commits) implements LoadMsg {
    }

    record BranchLogLoaded(String branch, List<Commit> commits) implements LoadMsg {
    }

    record CommitDetailLoaded(Sha sha, String changes) implements LoadMsg {
    }

    record FileDiffLoaded(List<FileEntry> files, Diffs diff) implements LoadMsg {
    }

    record Failed(Cmd.Load cmd, Failure failure) implements LoadMsg {
    }
}
