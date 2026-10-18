package net.vanfleteren.jazygit.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The readline commands, on text written with a {@code |} where the cursor is.
 */
class LineCommandTest {

    private static EditBuffer buffer(String marked) {
        String text = marked.replace("\\n", "\n");
        return new EditBuffer(text.replace("|", ""), text.indexOf('|'));
    }

    private static String marked(EditBuffer buffer) {
        return (buffer.text().substring(0, buffer.cursor()) + "|"
                        + buffer.text().substring(buffer.cursor()))
                .replace("\n", "\\n");
    }

    private static void assertCommand(LineCommand command, String before, String after) {
        assertEquals(after, marked(buffer(before).apply(command.on(buffer(before)))));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "feature/foo-ba|r > feature/foo-|bar",
                "feature/foo-|bar > feature/|foo-bar",
                "feature/|foo-bar > |feature/foo-bar",
                "|feature > |feature",
                "fix it  | > 'fix |it  '",
                "één twee| > één |twee",
                "first\\nsec|ond > first\\n|second",
                "first\\n|second > |first\\nsecond",
            })
    void backwardWordGoesToTheStartOfTheWordOrThePreviousOne(String before, String after) {
        assertCommand(LineCommand.BACKWARD_WORD, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "f|eature/foo-bar > feature|/foo-bar",
                "feature|/foo-bar > feature/foo|-bar",
                "feature/foo-bar| > feature/foo-bar|",
                "'|  fix it' > '  fix| it'",
                "fir|st\\nsecond > first|\\nsecond",
                "first|\\nsecond > first\\nsecond|",
            })
    void forwardWordGoesToTheEndOfTheWordOrTheNextOne(String before, String after) {
        assertCommand(LineCommand.FORWARD_WORD, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "git checkout feature/foo-bar| > git checkout |",
                "git checkout feature/foo|-bar > git checkout |-bar",
                "git checkout   | > git |",
                "|git > |git",
                "first\\n|second > |second",
            })
    void unixWordRuboutDeletesBackToWhitespace(String before, String after) {
        assertCommand(LineCommand.UNIX_WORD_RUBOUT, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "feature/foo-bar| > feature/foo-|",
                "feature/foo-| > feature/|",
                "|feature > |feature",
            })
    void backwardKillWordDeletesBackToTheStartOfAWord(String before, String after) {
        assertCommand(LineCommand.BACKWARD_KILL_WORD, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "|feature/foo-bar > |/foo-bar",
                "feature|/foo-bar > feature|-bar",
                "feature/foo-bar| > feature/foo-bar|",
            })
    void killWordDeletesToTheEndOfAWord(String before, String after) {
        assertCommand(LineCommand.KILL_WORD, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "fix |it > |it",
                "|fix it > |fix it",
                "first\\nsec|ond > first\\n|ond",
            })
    void unixLineDiscardDeletesToTheStartOfTheLine(String before, String after) {
        assertCommand(LineCommand.UNIX_LINE_DISCARD, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "fix| it > fix|",
                "fix it| > fix it|",
                "fi|rst\\nsecond > fi|\\nsecond",
                // At the end of a line, it does nothing, rather than join the next line.
                "first|\\nsecond > first|\\nsecond",
            })
    void killLineDeletesToTheEndOfTheLine(String before, String after) {
        assertCommand(LineCommand.KILL_LINE, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "fix |it > |fix it",
                "first\\nsec|ond > first\\n|second",
            })
    void beginningOfLineGoesToTheStartOfTheLine(String before, String after) {
        assertCommand(LineCommand.BEGINNING_OF_LINE, before, after);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '>',
            value = {
                "fix |it > fix it|",
                "fi|rst\\nsecond > first|\\nsecond",
            })
    void endOfLineGoesToTheEndOfTheLine(String before, String after) {
        assertCommand(LineCommand.END_OF_LINE, before, after);
    }
}
