package net.vanfleteren.jazygit.ui.edit;

import java.util.function.IntPredicate;

/**
 * The commands of readline that move in or delete from the text being typed, named as readline names them. Like
 * readline, they know two kinds of words: {@link #UNIX_WORD_RUBOUT} deletes back to whitespace, so all of
 * {@code feature/foo-bar}, while the other word commands stop at anything that is not a letter or a digit. The
 * line ones work on the line the cursor is on.
 */
public enum LineCommand {
    BEGINNING_OF_LINE,
    END_OF_LINE,
    BACKWARD_WORD,
    FORWARD_WORD,
    UNIX_WORD_RUBOUT,
    BACKWARD_KILL_WORD,
    KILL_WORD,
    UNIX_LINE_DISCARD,
    KILL_LINE;

    private static final IntPredicate WORD = LineCommand::isWordChar;
    private static final IntPredicate NOT_WORD = WORD.negate();
    private static final IntPredicate SPACE = Character::isWhitespace;
    private static final IntPredicate NOT_SPACE = SPACE.negate();
    private static final IntPredicate NOT_NEWLINE = c -> c != '\n';

    /**
     * What this command does to {@code buffer}.
     */
    public Edit on(EditBuffer buffer) {
        String text = buffer.text();
        int cursor = buffer.cursor();
        return switch (this) {
            case BEGINNING_OF_LINE -> new Edit.Move(lineStart(text, cursor));
            case END_OF_LINE -> new Edit.Move(lineEnd(text, cursor));
            case BACKWARD_WORD -> new Edit.Move(wordStart(text, cursor));
            case FORWARD_WORD -> new Edit.Move(wordEnd(text, cursor));
            case UNIX_WORD_RUBOUT -> new Edit.Delete(back(text, back(text, cursor, SPACE), NOT_SPACE), cursor);
            case BACKWARD_KILL_WORD -> new Edit.Delete(wordStart(text, cursor), cursor);
            case KILL_WORD -> new Edit.Delete(cursor, wordEnd(text, cursor));
            case UNIX_LINE_DISCARD -> new Edit.Delete(lineStart(text, cursor), cursor);
            case KILL_LINE -> new Edit.Delete(cursor, lineEnd(text, cursor));
        };
    }

    /**
     * Letters and digits, and the accents that can follow a letter.
     */
    private static boolean isWordChar(int c) {
        int type = Character.getType(c);
        return Character.isLetterOrDigit(c)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static int wordStart(String text, int cursor) {
        return back(text, back(text, cursor, NOT_WORD), WORD);
    }

    private static int wordEnd(String text, int cursor) {
        return forward(text, forward(text, cursor, NOT_WORD), WORD);
    }

    private static int lineStart(String text, int cursor) {
        return back(text, cursor, NOT_NEWLINE);
    }

    private static int lineEnd(String text, int cursor) {
        return forward(text, cursor, NOT_NEWLINE);
    }

    /**
     * Where moving back from {@code at} over the characters that match stops.
     */
    private static int back(String text, int at, IntPredicate matches) {
        return at > 0 && matches.test(text.codePointBefore(at))
                ? back(text, at - Character.charCount(text.codePointBefore(at)), matches)
                : at;
    }

    /**
     * Where moving forward from {@code at} over the characters that match stops.
     */
    private static int forward(String text, int at, IntPredicate matches) {
        return at < text.length() && matches.test(text.codePointAt(at))
                ? forward(text, at + Character.charCount(text.codePointAt(at)), matches)
                : at;
    }
}
