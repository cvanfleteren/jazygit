package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

/**
 * What a file in conflict can be handed to, and the commands that hand it (ADR 201).
 */
class ConflictToolsTest {

    @Test
    void theMergeToolKeepsNoBackupUnlessTheUserSaidWhetherTo() {
        assertEquals(
                List.of("git", "-c", "mergetool.keepBackup=false", "mergetool", "--", "a b"),
                new ConflictTools(true, false, false).mergeTool("a b"));
        assertEquals(List.of("git", "mergetool", "--", "a b"), new ConflictTools(true, true, false).mergeTool("a b"));
    }

    @Test
    void mergirafWritesTheConflictWithItsBaseThenSolvesItWithoutABackup() {
        assertEquals(
                List.of(
                        List.of("git", "checkout", "--conflict=diff3", "--", "a b"),
                        List.of("mergiraf", "solve", "--keep-backup=false", "a b")),
                ConflictTools.mergiraf("a b"));
    }

    @Test
    void aToolIsOfferedOnlyOnAFileBothSidesChanged() {
        ConflictTools all = new ConflictTools(true, false, true);
        Conflict both = new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED);
        Conflict deleted = new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED);

        assertEquals(List.of(true, true), List.of(all.mergesWithTool(both), all.solvesWithMergiraf(both)));
        assertEquals(List.of(false, false), List.of(all.mergesWithTool(deleted), all.solvesWithMergiraf(deleted)));
        assertEquals(
                List.of(false, false),
                List.of(ConflictTools.NONE.mergesWithTool(both), ConflictTools.NONE.solvesWithMergiraf(both)));
    }
}
