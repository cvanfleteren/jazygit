package net.vanfleteren.jazygit.features.changes.reflog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.StubStatus;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.model.MetadataStamp;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Env;
import org.junit.jupiter.api.Test;

/**
 * A read of the reflog brings what keeps the commits of the entries it read with them (ADR 188).
 */
class ReadReflogTest {

    private static final ReadReflog READ = new ReadReflog(0, 3);
    private static final List<ReflogEntry> ENTRIES = ReflogUpdateTest.entries(0, 3);

    @Test
    void theEntriesComeWithWhatKeepsTheirCommits() {
        Kept kept = new Kept(HashSet.of(ENTRIES.get(1).sha()), HashSet.empty());

        assertEquals(
                new ReflogMsg.Read(READ, new Reflog.Entries(ENTRIES), Option.of(kept), Option.none()),
                READ.run(new Env(git(Either.right(new Reflog.Entries(ENTRIES)), Either.right(kept)))));
    }

    @Test
    void whenWhatKeepsTheCommitsCannotBeFoundTheEntriesComeWithout() {
        assertEquals(
                new ReflogMsg.Read(READ, new Reflog.Entries(ENTRIES), Option.none(), Option.none()),
                READ.run(new Env(git(
                        Either.right(new Reflog.Entries(ENTRIES)),
                        Either.left(new GitError.CommandFailed("rev-list", 128))))));
    }

    @Test
    void anEmptyReflogKeepsNothing() {
        assertEquals(
                new ReflogMsg.Read(READ, Reflog.Empty.OFF, Option.of(Kept.BY_REFS), Option.none()),
                READ.run(
                        new Env(git(Either.right(Reflog.Empty.OFF), Either.left(new GitError.CommandFailed("x", 1))))));
    }

    @Test
    void aReadFromTheTopBringsTheStampOfWhatTheReflogIsReadFromAndALaterPageNone() {
        Git git = git(Either.right(new Reflog.Entries(ENTRIES)), Either.right(Kept.BY_REFS))
                .withStatus(new StubStatus() {
                    @Override
                    public Option<MetadataStamp> reflogStamp() {
                        return Option.of(ReflogUpdateTest.STAMP);
                    }
                });

        assertEquals(Option.of(ReflogUpdateTest.STAMP), ((ReflogMsg.Read) READ.run(new Env(git))).stamp());
        assertEquals(Option.none(), ((ReflogMsg.Read) new ReadReflog(3, 3).run(new Env(git))).stamp());
    }

    private static Git git(Either<GitError, Reflog> reflog, Either<GitError, Kept> kept) {
        return StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Reflog> reflog(int skip, int max) {
                return reflog;
            }

            @Override
            public Either<GitError, Kept> lost(Seq<Sha> commits) {
                return kept;
            }
        });
    }
}
