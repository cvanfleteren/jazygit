package net.vanfleteren.jazygit.gh.stacks;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.gh.process.GhToken;
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
                List.of(StackMutations.create(List.of(96, 97)).executed()),
                stacks.create(List.of(96, 97)).get());
        assertEquals(
                List.of(StackMutations.add(3, List.of(98)).executed()),
                stacks.add(3, List.of(98)).get());
        assertEquals(
                List.of(StackMutations.unstack(3).executed()), stacks.unstack(3).get());
        assertEquals(
                List.of(
                                StackMutations.create(List.of(96, 97)),
                                StackMutations.add(3, List.of(98)),
                                StackMutations.unstack(3))
                        .map(command -> String.join(
                                " ", command.executed().commandLine().tail()))
                        .toJavaList(),
                Files.readAllLines(dir.resolve("asked")));
    }
}
