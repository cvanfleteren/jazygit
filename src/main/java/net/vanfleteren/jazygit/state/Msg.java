package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.FileEntry;
import net.vanfleteren.jazygit.model.RepoStatus;

import java.util.List;

/**
 * Everything that can happen to the {@link Model}.
 */
public sealed interface Msg {

    /**
     * Periodic prompt to check the repository for changes made outside the app.
     */
    record Tick() implements Msg {
    }

    record StatusLoaded(RepoStatus status) implements Msg {
    }

    record BranchesLoaded(List<Branch> branches) implements Msg {
    }

    record CommitsLoaded(List<Commit> commits) implements Msg {
    }

    /**
     * The user highlighted a branch in the branches pane.
     */
    record BranchSelected(String branch) implements Msg {
    }

    record BranchLogLoaded(String branch, List<Commit> commits) implements Msg {
    }

    record LoadFailed(Cmd.Load cmd, String message) implements Msg {
    }

    /**
     * The user asked to check out a branch.
     */
    record CheckoutRequested(String branch) implements Msg {
    }

    record CheckedOut(String branch) implements Msg {
    }

    record CheckoutFailed(String branch, String message) implements Msg {
    }

    /**
     * The user asked to stage the given files, or to unstage them if they are all staged already.
     */
    record ToggleStageRequested(List<FileEntry> files) implements Msg {

        public ToggleStageRequested {
            files = List.copyOf(files);
        }
    }

    record StageToggled() implements Msg {
    }

    record StageToggleFailed(String message) implements Msg {
    }
}
