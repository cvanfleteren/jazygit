package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tag;
import net.vanfleteren.jazygit.git.model.Upstream;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parsing of {@code git for-each-ref} and {@code git log} output.
 */
class CliParseTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);

    private static String ref(String... fields) {
        return String.join("\u001f", fields) + "\n";
    }

    @Test
    void branchesKnowTheirUpstreamRemoteAndWhetherTheyAreTheDefault() {
        String raw = ref("refs/heads/feature", B, "200", " ", "origin/feature", "[ahead 1, behind 2]", "",
                "refs/remotes/origin/feature")
                + ref("refs/heads/gone", B, "100", " ", "origin/gone", "[gone]", "", "refs/remotes/origin/gone")
                + ref("refs/heads/trunk", A, "300", "*", "origin/trunk", "", "", "refs/remotes/origin/trunk")
                + ref("refs/remotes/origin/HEAD", A, "300", " ", "", "", "refs/remotes/origin/trunk", "")
                + ref("refs/remotes/origin/feature", B, "200", " ", "", "", "", "")
                + ref("refs/remotes/origin/trunk", A, "300", " ", "", "", "", "");

        assertEquals(List.of(
                        new Branch("trunk", true, new Sha(A), Instant.ofEpochSecond(300), true, true,
                                Option.of(new Upstream("origin/trunk", 0, 0))),
                        new Branch("feature", false, new Sha(B), Instant.ofEpochSecond(200), true, false,
                                Option.of(new Upstream("origin/feature", 1, 2))),
                        new Branch("gone", false, new Sha(B), Instant.ofEpochSecond(100), false, false, Option.none())),
                CliBranches.parse(raw).get());
    }

    @Test
    void aBranchIsOnItsRemoteWhenItsUpstreamIsEvenUnderAnotherName() {
        String raw = ref("refs/heads/local", A, "1", "*", "origin/elsewhere", "", "", "refs/remotes/origin/elsewhere")
                + ref("refs/remotes/origin/elsewhere", A, "1", " ", "", "", "", "");

        assertEquals(List.of(true), CliBranches.parse(raw).get().map(Branch::hasRemote));
    }

    @Test
    void withoutOriginHeadMainOrMasterIsTheDefault() {
        String raw = ref("refs/heads/master", A, "1", "*", "", "", "", "");

        assertEquals(List.of(true), CliBranches.parse(raw).get().map(Branch::isDefault));
    }

    @Test
    void commitsKeepTheirParentsSubjectAndBody() {
        String raw = String.join("\u001f", A, B, "Ada", "ada@example.com", "60", "Subject",
                "HEAD -> main, tag: v1, tag: v1.1, origin/main", "Subject\n\nBody\nmore\n")
                + "\0" + String.join("\u001f", B, "", "Bob", "bob@example.com", "0", "Root", "", "Root\n");

        assertEquals(List.of(
                        new Commit(new Sha(A), "Ada", "ada@example.com", Instant.ofEpochSecond(60), "Subject",
                                "Body\nmore", List.of(new Sha(B)), List.of(new Tag("v1"), new Tag("v1.1"))),
                        new Commit(new Sha(B), "Bob", "bob@example.com", Instant.EPOCH, "Root", "", List.empty())),
                CliLog.parse(raw).get());
    }

    @Test
    void malformedOutputIsAnErrorRatherThanAnException() {
        assertEquals(new GitError.UnexpectedOutput("refs/heads/main\u001f" + A),
                CliBranches.parse(ref("refs/heads/main", A)).getLeft());
        assertEquals(new GitError.UnexpectedOutput("soon"),
                CliBranches.parse(ref("refs/heads/main", A, "soon", "*", "", "", "", "")).getLeft());
        assertEquals(new GitError.UnexpectedOutput(" "),
                CliLog.parse(String.join("\u001f", " ", "", "Ada", "ada@example.com", "60", "S", "", "S\n")).getLeft());
        assertEquals(new GitError.UnexpectedOutput("stash@{0} without a separator"),
                CliStash.parse("stash@{0} without a separator\n").getLeft());
        assertEquals(new GitError.UnexpectedOutput("1 M"), CliStatus.parse("# branch.head main\0001 M\0").getLeft());
    }

    @Test
    void aShaIsNeitherBlankNorSpaced() {
        assertEquals(Option.of(new Sha(A)), Sha.parse(" " + A + "\n"));
        assertEquals(Option.none(), Sha.parse("  "));
        assertEquals(Option.none(), Sha.parse(A + " " + B));
    }
}
