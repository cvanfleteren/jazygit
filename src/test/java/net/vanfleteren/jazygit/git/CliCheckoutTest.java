package net.vanfleteren.jazygit.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThrows(LocalChangesException.class, () -> CliCheckout.checkout(repo, "feature"));
    }

    @Test
    void checkoutCarriesNonConflictingChangesAcrossWithoutAStash() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "changed\n");

        CliCheckout.checkout(repo, "feature");

        assertEquals("changed\n", Files.readString(repo.resolve("a.txt")));
    }

    @Test
    void otherCheckoutFailuresAreNotLocalChanges() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CliCheckout.checkout(repo, "no-such-branch"));
        assertEquals(false, e instanceof LocalChangesException);
    }

    @Test
    void checkoutWithStashBringsTheChangesAcrossAndLeavesNoStash() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "changed\n");

        CliCheckout.checkoutWithStash(repo, "feature");

        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals("changed\n", Files.readString(repo.resolve("a.txt")));
        assertEquals("", git("stash", "list"));
    }

    @Test
    void checkoutWithStashLeavesOlderStashesAloneWhenThereIsNothingToStash() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "old\n");
        git("stash", "push", "-q");

        CliCheckout.checkoutWithStash(repo, "feature");

        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals("one\n", Files.readString(repo.resolve("a.txt")));
        assertEquals(1, git("stash", "list").lines().count());
    }

    @Test
    void failedCheckoutPutsTheChangesBack() throws Exception {
        Files.writeString(repo.resolve("a.txt"), "changed\n");

        assertThrows(IllegalStateException.class, () -> CliCheckout.checkoutWithStash(repo, "no-such-branch"));

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

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CliCheckout.checkoutWithStash(repo, "feature"));

        assertEquals(true, e.getMessage().startsWith("Checked out, but your stashed changes"), e.getMessage());
        assertEquals("feature", git("branch", "--show-current").strip());
        assertEquals(1, git("stash", "list").lines().count());
    }

    private String git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        return new ProcessExecutor().command(command).directory(repo.toFile())
                .readOutput(true).redirectErrorStream(true).exitValueNormal().execute().outputUTF8();
    }
}
