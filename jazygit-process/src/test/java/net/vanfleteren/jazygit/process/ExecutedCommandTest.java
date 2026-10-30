package net.vanfleteren.jazygit.process;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExecutedCommandTest {

    @Test
    void aCommandOfPlainWordsIsWrittenAsItIs() {
        assertEquals(
                "git push origin refs/heads/main:main",
                ExecutedCommand.of("git", "push", "origin", "refs/heads/main:main")
                        .line());
    }

    @Test
    void anArgumentAShellWouldSplitOrExpandIsQuoted() {
        assertEquals(
                "git commit -m 'two words'",
                ExecutedCommand.of("git", "commit", "-m", "two words").line());
        assertEquals(
                "echo '$HOME' '*'", ExecutedCommand.of("echo", "$HOME", "*").line());
        assertEquals("echo ''", ExecutedCommand.of("echo", "").line());
    }

    @Test
    void aQuoteInAnArgumentIsWrittenOutsideTheQuotes() {
        assertEquals("echo 'it'\\''s'", ExecutedCommand.of("echo", "it's").line());
    }
}
