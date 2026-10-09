package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.Commit;
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

    record LoadFailed(Cmd cmd, String message) implements Msg {
    }
}
