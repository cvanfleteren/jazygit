package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

class DiffTextTest {

    private static final String DIFF = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1 +1 @@
            -count = 1
            +count = 2
            """;

    @Test
    void aDiffReadAgainIsEqualToTheOneReadBefore() {
        DiffText before = DiffText.of(DIFF);
        DiffText again = DiffText.of(new String(DIFF));

        assertNotSame(before.parsed(), again.parsed());
        assertEquals(before, again);
        assertEquals(before.hashCode(), again.hashCode());
    }

    @Test
    void aTruncatedDiffIsNotEqualToTheWholeOfTheSameText() {
        assertNotEquals(DiffText.of(DIFF), DiffText.of(DIFF, true));
    }
}
