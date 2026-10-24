package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;
import org.junit.jupiter.api.Test;

class ErrorTextTest {

    @Test
    void aStashThatCouldNotBeRestoredSaysTheCheckoutHappened() {
        var error = new StashNotRestored(new CommandFailed(Option.none(), 1, "conflict"));

        assertTrue(ErrorText.of(error).startsWith("Checked out, but your stashed changes"));
    }

    @Test
    void aCommitThatCameInThroughAMergeNamesTheMergeAndWhereTheBranchWouldBeLeft() {
        var error = new AppError.MovedThroughMerge(
                new Sha("aaaaaaaaaa"), new BranchName("main"), new Sha("bbbbbbbbbb"), new Sha("cccccccccc"));

        assertEquals(
                "aaaaaaa can't be moved: it came into main through the merge bbbbbbb. It isn't one of main's own"
                        + " commits, so moving it would leave main on ccccccc, a commit of the merged branch. Pick"
                        + " bbbbbbb or a commit above it, or one on main's own line.",
                ErrorText.of(error));
    }
}
