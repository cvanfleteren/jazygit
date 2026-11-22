package net.vanfleteren.jazygit.features.forgejo.pulls;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.HEAD;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerge;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FakeForgejo;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import org.junit.jupiter.api.Test;

/**
 * Merging a pull request on Forgejo, and why it failed (ADR 180).
 */
class MergeCommandsTest {

    private static final MergePullRequestCmd REBASE = new MergePullRequestCmd(
            ORIGIN, new BranchName("feature"), 12, ForgejoMergeStyle.REBASE, new FailedMerge.Standing(HEAD), true);

    private static Phrase phrase(int status, String body) {
        return new MergeFailure(new ForgejoError.Refused(status, body), "code.example.net", "cvf/nix").phrase();
    }

    @Test
    void itAsksForTheStyleTheHeadAsReadAndWhetherToDeleteTheBranch() {
        AtomicReference<ForgejoMerge> asked = new AtomicReference<>();
        FakeForgejo forgejo = FakeForgejo.of(details("Merge it")).merging(merge -> {
            asked.set(merge);
            return Either.right(new ForgejoMerged.Merged());
        });

        assertEquals(new PullRequestMsg.Merged(REBASE, new ForgejoMerged.Merged()), REBASE.run(forgejo.env()));
        assertEquals(new ForgejoMerge(ForgejoMergeStyle.REBASE, HEAD, true), asked.get());
    }

    @Test
    void whatForgejoRefusesFailsWithItsAnswerAndAHomeNoLongerOnForgejoFailsSo() {
        ForgejoError refused = new ForgejoError.Refused(405, "{\"message\":\"Work in progress PRs cannot be merged\"}");
        FakeForgejo forgejo = FakeForgejo.of(details("Merge it")).merging(merge -> Either.left(refused));

        assertEquals(
                new PullRequestMsg.MergeFailed(REBASE, new MergeFailure(refused, "code.example.net", "cvf/nix")),
                REBASE.run(forgejo.env()));
        assertEquals(
                new PullRequestMsg.MergeFailed(REBASE, new ForgejoFailure.NoHome()),
                REBASE.run(forgejo.env(HashMap.of(ORIGIN, "git@github.com:cvf/nix.git"))));
    }

    @Test
    void itIsAnOperationOnTheBranchThatChangesTheForge() {
        assertTrue(REBASE.changesForge());
        assertEquals(Option.of(new BranchRef.Local(new BranchName("feature"))), REBASE.busyBranch());
    }

    @Test
    void theErrorsSayWhatToDo() {
        assertEquals(
                Phrase.of("forgejo.error.merge.token", "code.example.net"),
                phrase(401, "{\"message\":\"token is required\"}"));
        assertEquals(
                Phrase.of("forgejo.error.merge.forbidden", "cvf/nix", "user should have specific permission"),
                phrase(403, "{\"message\":\"user should have specific permission\"}"));
        assertEquals(
                Phrase.of(
                        "forgejo.error.merge.notAllowed",
                        "not allowed to merge [reason: Not all required status checks successful]"),
                phrase(
                        405,
                        "{\"message\":\"not allowed to merge [reason: Not all required status checks successful]\"}"));
        assertEquals(Phrase.of("forgejo.error.merge.moved"), phrase(409, "{\"message\":\"head out of date\"}"));
        assertEquals(
                Phrase.of("forgejo.error.merge.conflicts"),
                phrase(409, "{\"Style\":\"merge\",\"StdOut\":\"\",\"StdErr\":\"CONFLICT\",\"Err\":{}}"));
        assertEquals(
                Phrase.of("forgejo.error.merge.refused", "409", "merge push out of date"),
                phrase(409, "{\"message\":\"merge push out of date\"}"));
    }

    @Test
    void onlyAHeadThatMovedOnIsTold() {
        assertTrue(new MergeFailure(new ForgejoError.Refused(409, "{\"message\":\"head out of date\"}"), "h", "r")
                .moved());
        assertFalse(new MergeFailure(new ForgejoError.Refused(409, "{\"Style\":\"merge\"}"), "h", "r").moved());
        assertFalse(new MergeFailure(new ForgejoError.Unreachable("head out of date"), "h", "r").moved());
    }
}
