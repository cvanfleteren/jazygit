package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.LocalChanges;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import net.vanfleteren.jazygit.ui.ErrorText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliCheckoutTest {

    @TempDir
    Path repo;

    @BeforeEach
    void setUp() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        Files.writeString(repo.resolve("a.txt"), "one\n");
        git("add", ".");
        git("commit", "-qm", "init");
        git("branch", "feature");
    }

    @Test
    void checkoutRefusedForLocalChangesIsReportedAsSuch() throws Exception {
        git("checkout", "-q", "feature");
        Files.writeString(repo.resolve("a.txt"), "feature\n");
        git("commit", "-qam", "feature change");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("a.txt"), "main change\n");

        assertInstanceOf(LocalChanges.class, CliCheckout.checkout(repo, "feature").getLeft().error());
    }

    @Test
    void checkoutCarriesNonConflictingChangesAcrossWithoutAStash() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "changed\n");

        assertTrue(CliCheckout.checkout(repo, "feature").isRight());

        assertEquals("changed\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void otherCheckoutFailuresAreNotLocalChanges() {
        assertInstanceOf(CommandFailed.class, CliCheckout.checkout(repo, "no-such-branch").getLeft().error());
    }

    @Test
    void checkoutWithStashBringsTheChangesAcrossAndLeavesNoStash() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "changed\n");

        assertEquals(List.of("git stash push --quiet", "git checkout --quiet feature --", "git stash pop --quiet"),
                CliCheckout.checkoutWithStash(repo, "feature").get().map(ExecutedCommand::line));

        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals("changed\n", Files.readString(repo.resolve("a.txt")));
        assertEquals("", git("stash", "list"));
    }

    @Test
    void checkoutWithStashLeavesOlderStashesAloneWhenThereIsNothingToStash() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "old\n");
        git("stash", "push", "-q");

        assertTrue(CliCheckout.checkoutWithStash(repo, "feature").isRight());

        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals("one\n", Files.readString(repo.resolve("a.txt")));
        assertEquals(1, git("stash", "list").lines().count());
    }

    @Test
    void failedCheckoutPutsTheChangesBack() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "changed\n");

        GitFailure<GitError.StashCheckoutError> failure = CliCheckout.checkoutWithStash(repo, "no-such-branch").getLeft();

        assertInstanceOf(CommandFailed.class, failure.error());
        assertEquals(List.of("git stash push --quiet", "git checkout --quiet no-such-branch --", "git stash pop --quiet"),
                failure.commands().map(ExecutedCommand::line));

        assertEquals("main", git("branch", "--show-current").strip());
        assertEquals("changed\n", Files.readString(repo.resolve("a.txt")));
        assertEquals("", git("stash", "list"));
    }

    @Test
    void aConflictWhilePoppingKeepsTheChangesInTheStash() throws Exception {
        git("checkout", "-q", "feature");
        Files.writeString(repo.resolve("a.txt"), "feature\n");
        git("commit", "-qam", "feature change");
        git("checkout", "-q", "main");
        Files.writeString(repo.resolve("a.txt"), "main change\n");

        GitFailure<GitError.StashCheckoutError> failure = CliCheckout.checkoutWithStash(repo, "feature").getLeft();

        assertInstanceOf(StashNotRestored.class, failure.error());
        assertTrue(ErrorText.of(failure.error()).startsWith("Checked out, but your stashed changes"));
        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals(1, git("stash", "list").lines().count());
    }

    private String git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of("git"));
        command.addAll(java.util.List.of(args));
        return new ProcessExecutor().command(command).directory(repo.toFile())
                .readOutput(true).redirectErrorStream(true).exitValueNormal().execute().outputUTF8();
    }
}
