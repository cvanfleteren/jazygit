package net.vanfleteren.jazygit.core.gh.stacks;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.gh.CliGitHub;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.gh.process.GhToken;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The stacks of pull requests through gh (ADR 114); the commands themselves are in {@link StackMutationsTest}.
 */
class CliStacksTest {

    // Stacks through a gh that writes what it was asked with to the file "asked" in dir.
    private static Stacks stacks(Path dir) throws Exception {
        Path gh = Files.writeString(dir.resolve("gh"), "#!/bin/sh\necho \"$*\" >> '" + dir.resolve("asked") + "'\n");
        gh.toFile().setExecutable(true);
        return CliGitHub.of(dir, gh.toString(), GhToken.NONE).stacks();
    }

    @Test
    void eachChangeToTheStacksRunsItsCommandAndLogsIt(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");
        Stacks stacks = stacks(dir);

        assertEquals(
                List.of(StackMutations.create(List.of(new PrNumber(96), new PrNumber(97)))
                        .executed()),
                stacks.create(List.of(new PrNumber(96), new PrNumber(97))).get());
        assertEquals(
                List.of(StackMutations.add(new StackId(3), List.of(new PrNumber(98)))
                        .executed()),
                stacks.add(new StackId(3), List.of(new PrNumber(98))).get());
        assertEquals(
                List.of(StackMutations.unstack(new StackId(3)).executed()),
                stacks.unstack(new StackId(3)).get());
        assertEquals(
                List.of(
                                StackMutations.create(List.of(new PrNumber(96), new PrNumber(97))),
                                StackMutations.add(new StackId(3), List.of(new PrNumber(98))),
                                StackMutations.unstack(new StackId(3)))
                        .map(command -> String.join(
                                " ", command.executed().commandLine().tail()))
                        .toJavaList(),
                Files.readAllLines(dir.resolve("asked")));
    }
}
