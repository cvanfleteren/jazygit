package net.vanfleteren.jazygit.git.history;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tag;
import net.vanfleteren.jazygit.git.process.GitError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * Reading the history through git, where the role's operations alone cannot show it.
 */
class CliHistoryTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);

    @TempDir
    Path repo;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        git("commit", "-q", "--allow-empty", "-m", "Initial commit");
    }

    @Test
    void moreCommitsNotOnTheBaseThanAreReadAreCountedAsMoreWithoutAForkPoint() throws Exception {
        git("checkout", "-q", "-b", "side");
        git("commit", "-q", "--allow-empty", "-m", "Side one");
        git("commit", "-q", "--allow-empty", "-m", "Side two");
        git("commit", "-q", "--allow-empty", "-m", "Side three");
        BranchRef main = new BranchRef.Local(new BranchName("main"));
        Sha head = tip("side");

        Ahead three = CliHistory.ahead(repo, head, main, tip("main"), 3).get();
        Ahead two = CliHistory.ahead(repo, head, main, tip("main"), 2).get();

        assertFalse(three.more());
        assertTrue(two.more());
        assertEquals(List.of("Side three", "Side two"), two.commits().map(Commit::message));
        assertEquals(head, two.tip());
        // The walk cut short stops at Side one too, so the fork point is asked of git merge-base.
        assertEquals(three.forkPoint(), two.forkPoint());
        assertEquals(Option.of(tip("main")), two.forkPoint().map(Commit::sha));
    }

    private Sha tip(String branch) throws Exception {
        return new Sha(git("rev-parse", "refs/heads/" + branch).strip());
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor()
                .command(command)
                .directory(repo.toFile())
                .readOutput(true)
                .redirectErrorStream(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }

    @Test
    void commitsKeepTheirParentsSubjectAndBody() {
        String raw = String.join(
                        "\u001f",
                        A,
                        B,
                        "Ada",
                        "ada@example.com",
                        "60",
                        "Subject",
                        "HEAD -> main, tag: v1, tag: v1.1, origin/main",
                        "Subject\n\nBody\nmore\n")
                + "\0" + String.join("\u001f", B, "", "Bob", "bob@example.com", "0", "Root", "", "Root\n");

        assertEquals(
                List.of(
                        new Commit(
                                new Sha(A),
                                "Ada",
                                "ada@example.com",
                                Instant.ofEpochSecond(60),
                                "Subject",
                                "Body\nmore",
                                List.of(new Sha(B)),
                                List.of(new Tag("v1"), new Tag("v1.1"))),
                        Fixtures.commit(new Sha(B), "Bob", "bob@example.com", Instant.EPOCH, "Root", "", List.empty())),
                CliHistory.parse(raw).get());
    }

    @Test
    void aWalkTellsTheCommitsItListedFromThoseItStoppedAt() {
        String raw = String.join("\u001f", ">", A, B, "Ada", "ada@example.com", "60", "Side", "", "Side\n") + "\0"
                + String.join("\u001f", "-", B, "", "Bob", "bob@example.com", "0", "Root", "", "Root\n");

        CliHistory.Walk walk = CliHistory.parseWalk(raw).get();

        assertEquals(List.of(new Sha(A)), walk.commits().map(Commit::sha));
        assertEquals(List.of(new Sha(B)), walk.boundary().map(Commit::sha));
    }

    @Test
    void theParentsOfTheCommitsAreReadAsFullShasOnly() {
        assertEquals(
                List.of(List.of(new Sha(A), new Sha(B)), List.of(new Sha(B))),
                CliHistory.parents(A + " " + B + "\n" + B + "\n").get());
        assertTrue(CliHistory.parents(A + " " + B + "\nwarning: something\n").isLeft());
    }

    @Test
    void malformedOutputIsAnErrorRatherThanAnException() {
        assertEquals(
                new GitError.UnexpectedOutput(" "),
                CliHistory.parse(String.join("\u001f", " ", "", "Ada", "ada@example.com", "60", "S", "", "S\n"))
                        .getLeft());
    }
}
