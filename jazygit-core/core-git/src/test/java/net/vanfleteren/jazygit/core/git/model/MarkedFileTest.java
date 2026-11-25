package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

class MarkedFileTest {

    @Test
    void eachBlockStartsAtItsOpeningMarker() {
        String text =
                "a\n<<<<<<< HEAD\nb\n=======\nc\n>>>>>>> 9b07d31 (x)\nd\n<<<<<<< HEAD\ne\n=======\n>>>>>>> 9b07d31\n";

        assertEquals(List.of(2, 8), MarkedFile.blockStarts(text));
    }

    @Test
    void aMarkdownUnderlineIsNoBlock() {
        assertEquals(List.empty(), MarkedFile.blockStarts("Title\n=======\ntext\n"));
    }

    @Test
    void anOpeningMarkerThatIsNeverClosedIsNoBlock() {
        assertEquals(List.empty(), MarkedFile.blockStarts("<<<<<<< HEAD\nb\n<<<<<<< again\n"));
    }

    @Test
    void theBaseOfADiff3BlockStaysInsideIt() {
        String text = "<<<<<<< HEAD\nb\n||||||| base\na\n=======\nc\n>>>>>>> them\n";

        assertEquals(List.of(1), MarkedFile.blockStarts(text));
    }
}
