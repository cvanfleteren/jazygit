package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * The parents of the branches, and how far each local branch is from its base: read with every read of the
 * branches, from the branches and the parents just read, and kept until a read replaces them (ADR 46, ADR 56).
 */
class BaseDistanceUpdateTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BaseDistance DISTANCE = new BaseDistance(new BranchRef.Local(MAIN), new Sha("1"), 1, 58);
    private static final Map<BranchName, BranchName> PARENTS = HashMap.of(FEATURE, MAIN);
    private static final AllBranches READ = new AllBranches(BRANCHES, List.empty());

    @Test
    void theBranchesAreCountedWithTheParentsAndTheTipsJustRead() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<ProcessError, AllBranches> allBranches() {
                return Either.right(READ);
            }

            @Override
            public Either<ProcessError, Map<BranchName, BranchName>> parents() {
                return Either.right(PARENTS);
            }

            @Override
            public Map<BranchName, BaseDistance> baseDistance(
                    Map<BranchRef, Sha> tips, Map<BranchName, BranchName> parents, Option<BranchName> main) {
                return tips.equals(READ.tips()) && parents.equals(PARENTS) && main.equals(READ.main())
                        ? HashMap.of(FEATURE, DISTANCE)
                        : HashMap.empty();
            }
        };

        assertEquals(
                new LoadMsg.BranchesLoaded(READ, Option.of(new LoadMsg.Bases(PARENTS, HashMap.of(FEATURE, DISTANCE)))),
                new Cmd.LoadBranches().run(new Env(git, StubGitHub.empty())));
    }

    @Test
    void branchesWhoseParentsCannotBeReadComeBackWithoutThem() {
        StubGitProvider git = new StubGitProvider() {
            @Override
            public Either<ProcessError, AllBranches> allBranches() {
                return Either.right(READ);
            }

            @Override
            public Either<ProcessError, Map<BranchName, BranchName>> parents() {
                return Either.left(new GitError.UnexpectedOutput("garbled"));
            }
        };

        assertEquals(
                new LoadMsg.BranchesLoaded(READ, Option.none()),
                new Cmd.LoadBranches().run(new Env(git, StubGitHub.empty())));
    }

    @Test
    void noDistanceIsKnownBeforeTheFirstRead() {
        assertEquals(HashMap.empty(), loaded().repo().baseDistance());
    }

    @Test
    void theParentsAndTheCountsReadWithTheBranchesAreKept() {
        Model read = Update.update(loaded(), withBases(PARENTS, HashMap.of(FEATURE, DISTANCE)))
                .model();

        assertEquals(PARENTS, read.repo().parents());
        assertEquals(HashMap.of(FEATURE, DISTANCE), read.repo().baseDistance());
    }

    @Test
    void branchesReadWithoutTheirParentsKeepTheParentsAndTheCountsReadBefore() {
        Model read = Update.update(loaded(), withBases(PARENTS, HashMap.of(FEATURE, DISTANCE)))
                .model();

        Model again = Update.update(read, branchesLoaded(READ)).model();

        assertEquals(PARENTS, again.repo().parents());
        assertEquals(HashMap.of(FEATURE, DISTANCE), again.repo().baseDistance());
    }

    @Test
    void aReadOfTheBranchesThatFailsKeepsTheParentsAndTheCountsReadBefore() {
        Model read = Update.update(loaded(), withBases(PARENTS, HashMap.of(FEATURE, DISTANCE)))
                .model();

        Model failed = Update.update(
                        read,
                        new LoadMsg.Failed(
                                new Cmd.LoadBranches(), new Failure.Git(new GitError.UnexpectedOutput("garbled"))))
                .model();

        assertEquals(PARENTS, failed.repo().parents());
        assertEquals(HashMap.of(FEATURE, DISTANCE), failed.repo().baseDistance());
    }

    private static LoadMsg.BranchesLoaded withBases(
            Map<BranchName, BranchName> parents, Map<BranchName, BaseDistance> counts) {
        return new LoadMsg.BranchesLoaded(READ, Option.of(new LoadMsg.Bases(parents, counts)));
    }
}
