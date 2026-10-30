package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RevisionTest {

    private static final BranchName MAIN = new BranchName("main");

    @Test
    void aBranchIsGivenToGitAsItsFullRefAndShownByName() {
        Revision local = new BranchRef.Local(MAIN);
        Revision remote = new RemoteBranch("origin", MAIN).tracking();

        assertEquals("refs/heads/main", local.toArg());
        assertEquals("main", local.label());
        assertEquals("refs/remotes/origin/main", remote.toArg());
        assertEquals("origin/main", remote.label());
    }

    @Test
    void aCommitIsGivenToGitInFullAndShownShort() {
        Revision commit = new Revision.Commit(new Sha("0123456789abcdef"));

        assertEquals("0123456789abcdef", commit.toArg());
        assertEquals("0123456", commit.label());
    }

    @Test
    void headIsHead() {
        assertEquals("HEAD", new Revision.Head().toArg());
    }

    @Test
    void aResetTargetIsShownAsItsRevisionIs() {
        assertEquals("abc1234", new ResetTarget.ToCommit(new Sha("abc1234ffff")).label());
        assertEquals("origin/main", new ResetTarget.ToUpstream(new RemoteBranch("origin", MAIN).tracking()).label());
    }
}
