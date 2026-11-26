package net.vanfleteren.jazygit.kernel.state.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.model.Blame;
import net.vanfleteren.jazygit.core.git.model.BlameAt;
import net.vanfleteren.jazygit.core.git.model.BlameOptions;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.BaseTip;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile.Source;
import org.junit.jupiter.api.Test;

/**
 * Each version is blamed at the revision its lines are of (ADR 147).
 */
class ReadBlameTest {

    private static final Sha SHA = new Sha("aaaa");
    private static final Sha BASE = new Sha("bbbb");
    private static final CommitFile FILE = new CommitFile(new RepoPath("a.txt"), ChangeType.MODIFIED);
    private static final CommitFile RENAMED =
            new CommitFile(new RepoPath("a.txt"), ChangeType.RENAMED, Option.of(new RepoPath("old.txt")));
    private static final CommitFile ADDED = new CommitFile(new RepoPath("a.txt"), ChangeType.ADDED);
    private static final FileEntry ENTRY = new FileEntry(new RepoPath("a.txt"), ChangeType.MODIFIED, true, true);
    private static final PullRequestCommits COMMITS =
            new PullRequestCommits(new PrNumber(7), new RepoSlug("owner/repo"), "", BASE, SHA);
    private static final DiffText WHOLE =
            DiffText.of("diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,2 +1,2 @@\n one\n-two\n+Two\n");

    @Test
    void aCommitIsBlamedAtItselfAndItsOriginalAtItsFirstParentUnderItsOldPath() {
        assertEquals(
                List.of(new BlameAt.InCommit(SHA, new RepoPath("a.txt"))),
                at(new Source.OfCommit(SHA, FILE), FileVersion.NEW));
        assertEquals(
                List.of(new BlameAt.InCommit(SHA, 1, new RepoPath("old.txt"))),
                at(new Source.OfCommit(SHA, RENAMED), FileVersion.ORIGINAL));
        assertEquals(
                List.of(new BlameAt.InCommit(SHA, 1, new RepoPath("a.txt"))),
                at(new Source.OfFiles(new Changes.OfTip(new BranchName("main"), SHA), FILE), FileVersion.ORIGINAL));
    }

    @Test
    void aFileAStashAddedIsInTheStashOrAmongItsUntrackedFiles() {
        assertEquals(
                List.of(new BlameAt.InCommit(SHA, new RepoPath("a.txt"))),
                at(new Source.OfStash(SHA, FILE), FileVersion.NEW));
        assertEquals(
                List.of(
                        new BlameAt.InCommit(SHA, new RepoPath("a.txt")),
                        new BlameAt.InCommit(SHA, 3, new RepoPath("a.txt"))),
                at(new Source.OfFiles(new Changes.OfStash(SHA), ADDED), FileVersion.NEW));
        assertEquals(
                List.of(new BlameAt.InCommit(SHA, 1, new RepoPath("a.txt"))),
                at(new Source.OfStash(SHA, FILE), FileVersion.ORIGINAL));
    }

    @Test
    void theOriginalOfABranchOrAPullRequestIsBlamedAtItsMergeBase() {
        Source branch = new Source.OfFiles(
                new Changes.OfBranch(
                        new BranchName("feature"), new BaseTip(new BranchRef.Local(new BranchName("main")), BASE), SHA),
                FILE);
        assertEquals(List.of(new BlameAt.InCommit(SHA, new RepoPath("a.txt"))), at(branch, FileVersion.NEW));
        assertEquals(
                List.of(new BlameAt.AtMergeBase(BASE, SHA, new RepoPath("a.txt"))), at(branch, FileVersion.ORIGINAL));
        assertEquals(
                List.of(new BlameAt.AtMergeBase(BASE, SHA, new RepoPath("a.txt"))),
                at(new Source.OfFiles(new Changes.OfPullRequest(COMMITS), FILE), FileVersion.ORIGINAL));
    }

    @Test
    void theWorkingTreeIsBlamedOnDiskInTheIndexAndAtHead() {
        assertEquals(
                List.of(new BlameAt.OnDisk(new RepoPath("a.txt"))),
                at(new Source.WorkingTree(ENTRY, false), FileVersion.NEW));
        assertEquals(
                List.of(new BlameAt.InIndex(new RepoPath("a.txt"))),
                at(new Source.WorkingTree(ENTRY, false), FileVersion.ORIGINAL));
        assertEquals(
                List.of(new BlameAt.InIndex(new RepoPath("a.txt"))),
                at(new Source.WorkingTree(ENTRY, true), FileVersion.NEW));
        assertEquals(
                List.of(new BlameAt.AtHead(new RepoPath("a.txt"))),
                at(new Source.WorkingTree(ENTRY, true), FileVersion.ORIGINAL));
    }

    @Test
    void aFileWithoutChangesIsBlamedWhereItIsRead() {
        assertEquals(
                List.of(new BlameAt.OnDisk(new RepoPath("a.txt"))),
                at(new Source.OnDisk(new RepoPath("a.txt")), FileVersion.NEW));
        assertEquals(
                List.of(new BlameAt.InCommit(SHA, new RepoPath("a.txt"))),
                at(new Source.AtCommit(SHA, new RepoPath("a.txt")), FileVersion.NEW));
    }

    @Test
    void theFirstRevisionGitCanBlameTheFileAtWins() {
        Blame found = Blame.uncommitted(1, new RepoPath("a.txt"));
        Env env = new Env(StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Blame> blame(BlameAt at, BlameOptions options) {
                return at instanceof BlameAt.InCommit(var sha, int parent, var path) && parent == 3
                        ? Either.right(found)
                        : Either.left(new GitError.UnexpectedOutput("fatal: no such path"));
            }
        }));
        ReadBlame read = new ReadBlame(
                new AreaId("area"),
                file(new Source.OfStash(SHA, ADDED)),
                FileVersion.NEW,
                at(new Source.OfStash(SHA, ADDED), FileVersion.NEW),
                BlameOptions.DEFAULT);

        assertEquals(new LoadMsg.BlameRead(read, found), read.run(env));
    }

    @Test
    void onABranchWithoutCommitsTheWorkingTreeIsNotCommittedYetWithoutReadingBlame() {
        RepoStatus unborn = new RepoStatus(new Head.OnBranch(new BranchName("main"), Option.none()), List.of(ENTRY));
        Model model = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(unborn))
                .model();
        Version version = new Version(
                        new ScrolledContent.Diff(WHOLE),
                        new RepoPath("a.txt"),
                        Option.of(FileVersion.NEW),
                        file(new Source.WorkingTree(ENTRY, false)),
                        new Loadable.Loaded<>(WHOLE))
                .blamed(true, false);

        Next blamed = ReadBlame.blamed(model.mapRepoView(view -> view.withVersion(AreaId.UNSTAGED, version)));

        assertTrue(blamed.cmds().filter(ReadBlame.class::isInstance).isEmpty());
        Blame none = blamed.model()
                .repoView()
                .versionOf(AreaId.UNSTAGED)
                .flatMap(Version::blame)
                .flatMap(Loadable::toOption)
                .get();
        assertEquals(
                Vector.of(false, false), none.lines().map(line -> line.commit().committed()));
    }

    private static List<BlameAt> at(Source source, FileVersion version) {
        return ReadBlame.at(source, version, new RepoPath("a.txt"));
    }

    private static WholeFile file(Source source) {
        return new WholeFile(new ScrolledContent.Diff(WHOLE), source, new RepoPath("a.txt"), DiffOptions.DEFAULT);
    }
}
