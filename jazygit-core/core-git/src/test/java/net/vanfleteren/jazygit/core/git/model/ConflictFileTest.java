package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How a file in conflict stands against the conflict git wrote of it, for mergiraf to start from (ADR 201).
 */
class ConflictFileTest {

    private static final String WRITTEN =
            "a\n<<<<<<< ours\nmain\n=======\nfeature\n>>>>>>> theirs\nb\n<<<<<<< ours\nx\n=======\ny\n>>>>>>> theirs\n";

    @Test
    void aFileAsGitWroteItIsAsWrittenWhateverItsMarkersAreLabelled() {
        String asStopped = "a\n<<<<<<< HEAD\nmain\n=======\nfeature\n>>>>>>> 1234567 (Change one)\nb\n"
                + "<<<<<<< HEAD\nx\n=======\ny\n>>>>>>> 1234567 (Change one)\n";

        assertEquals(ConflictFile.AS_WRITTEN, ConflictFile.of(asStopped, WRITTEN));
    }

    @Test
    void lineEndingsAreNotEdits() {
        assertEquals(ConflictFile.AS_WRITTEN, ConflictFile.of(WRITTEN.replace("\n", "\r\n"), WRITTEN));
    }

    @Test
    void aFileWithABlockMergedByHandIsEdited() {
        String halfMerged = "a\nmain and feature\nb\n<<<<<<< HEAD\nx\n=======\ny\n>>>>>>> 1234567 (Change one)\n";

        assertEquals(ConflictFile.EDITED, ConflictFile.of(halfMerged, WRITTEN));
    }

    @Test
    void aFileWhoseEveryConflictHasItsBaseIsReadAsItIs() {
        String withBase = "a\n<<<<<<< HEAD\nmain\n||||||| base\nbase\n=======\nfeature\n>>>>>>> theirs\nmerged\n";

        assertEquals(ConflictFile.WITH_BASE, ConflictFile.of(withBase, WRITTEN));
    }

    @Test
    void aFileWithAConflictWithoutItsBaseIsNotReadAsItIs() {
        String mixed = "<<<<<<< HEAD\nmain\n||||||| base\nbase\n=======\nfeature\n>>>>>>> theirs\n"
                + "<<<<<<< HEAD\nx\n=======\ny\n>>>>>>> theirs\n";

        assertEquals(ConflictFile.EDITED, ConflictFile.of(mixed, WRITTEN));
    }
}
