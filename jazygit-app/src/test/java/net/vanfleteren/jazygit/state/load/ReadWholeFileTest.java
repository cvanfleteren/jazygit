package net.vanfleteren.jazygit.state.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.StubDiffs;
import net.vanfleteren.jazygit.git.files.StubStashes;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.diff.BaseTip;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.WholeFile.Source;
import org.junit.jupiter.api.Test;

/**
 * A file read whole is read as the diff it is shown in was, with the whole file as context (ADR 77).
 */
class ReadWholeFileTest {

    private static final Sha SHA = new Sha("aaaa");
    private static final Sha BASE = new Sha("bbbb");
    private static final CommitFile FILE = new CommitFile("a.txt", ChangeType.MODIFIED);
    private static final FileEntry ENTRY = Fixtures.fileEntry("a.txt", ChangeType.MODIFIED);
    private static final PullRequestCommits COMMITS = new PullRequestCommits(7, "owner/repo", "", BASE, SHA);

    @Test
    void eachDiffIsReadAgainTheWayItWasReadWithTheWholeFileAsContext() {
        assertEquals("commit aaaa a.txt -U1000000", read(new Source.OfCommit(SHA, FILE)));
        assertEquals("stash aaaa a.txt -U1000000", read(new Source.OfStash(SHA, FILE)));
        assertEquals("commit aaaa a.txt -U1000000", read(new Source.OfFiles(new Changes.OfCommit(SHA), FILE)));
        assertEquals("stash aaaa a.txt -U1000000", read(new Source.OfFiles(new Changes.OfStash(SHA), FILE)));
        assertEquals(
                "range bbbb aaaa a.txt -U1000000",
                read(new Source.OfFiles(
                        new Changes.OfBranch(
                                new BranchName("feature"),
                                new BaseTip(new BranchRef.Local(new BranchName("main")), BASE),
                                SHA),
                        FILE)));
        assertEquals(
                "pull request 7 a.txt -U1000000", read(new Source.OfFiles(new Changes.OfPullRequest(COMMITS), FILE)));
    }

    @Test
    void aFileOfTheWorkingTreeIsReadFromTheSideItIsShownOn() {
        assertEquals("staged a.txt -U1000000", read(new Source.WorkingTree(ENTRY, true)));
        assertEquals("unstaged a.txt -U1000000", read(new Source.WorkingTree(ENTRY, false)));
    }

    /**
     * What reading the file whole from {@code source} read: which read of git it was, and with what context.
     */
    private static String read(Source source) {
        ReadWholeFile read = new ReadWholeFile(
                new AreaId("area"), new ScrolledContent.Diff(DiffText.of("")), source, "a.txt", DiffOptions.DEFAULT);
        LoadMsg.WholeFileRead msg = (LoadMsg.WholeFileRead) read.run(new Env(git(), StubGitHub.of()));
        assertEquals(read, msg.cmd());
        return msg.whole().text();
    }

    private static net.vanfleteren.jazygit.git.Git git() {
        return StubGit.of()
                .withDiffs(new StubDiffs() {
                    @Override
                    public Either<GitError, DiffText> commitFileChanges(
                            Sha sha, List<CommitFile> files, DiffOptions options) {
                        return said("commit " + sha.value(), files, options);
                    }

                    @Override
                    public Either<GitError, DiffText> rangeFileChanges(
                            Sha base, Sha tip, List<CommitFile> files, DiffOptions options) {
                        return said("range " + base.value() + " " + tip.value(), files, options);
                    }

                    @Override
                    public Either<GitError, DiffText> pullRequestFileChanges(
                            PullRequestCommits commits, List<CommitFile> files, DiffOptions options) {
                        return said("pull request " + commits.number(), files, options);
                    }

                    @Override
                    public Either<GitError, Diffs> uncommitted(List<FileEntry> files, DiffOptions options) {
                        String paths = files.map(FileEntry::path).mkString(" ");
                        String context = options.context().flag();
                        return Either.right(
                                new Diffs("staged " + paths + " " + context, "unstaged " + paths + " " + context));
                    }
                })
                .withStashes(new StubStashes() {
                    @Override
                    public Either<GitError, DiffText> fileChanges(
                            Sha sha, List<CommitFile> files, DiffOptions options) {
                        return said("stash " + sha.value(), files, options);
                    }
                });
    }

    private static Either<GitError, DiffText> said(String read, List<CommitFile> files, DiffOptions options) {
        return Either.right(DiffText.of(read + " " + files.map(CommitFile::path).mkString(" ") + " "
                + options.context().flag()));
    }
}
