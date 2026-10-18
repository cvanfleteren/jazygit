package net.vanfleteren.jazygit.ui.edit;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The keys of readline that edit the text in a field, beyond the ones the field already knows, and what they do to
 * the state of the field. The fields of the toolkit take Ctrl+Left as a plain Left, so these keys have to reach
 * them first.
 *
 * <p>Alt+Backspace only works where the terminal input tells it from Escape followed by Backspace.
 */
public final class LineEdit {

    private LineEdit() {}

    /**
     * The command {@code event} is the key of, if any.
     */
    public static Option<LineCommand> command(KeyEvent event) {
        if (event.code() == KeyCode.CHAR) {
            return event.hasCtrl() && !event.hasAlt()
                    ? ctrl(Character.toLowerCase(event.codePoint()))
                    : event.hasAlt() && !event.hasCtrl() ? alt(event.codePoint()) : Option.none();
        }
        return switch (event.code()) {
            case LEFT -> Option.when(event.hasCtrl() && !event.hasAlt(), LineCommand.BACKWARD_WORD);
            case RIGHT -> Option.when(event.hasCtrl() && !event.hasAlt(), LineCommand.FORWARD_WORD);
            case DELETE -> Option.when(event.hasCtrl() && !event.hasAlt(), LineCommand.KILL_WORD);
            case BACKSPACE -> Option.when(event.hasAlt() && !event.hasCtrl(), LineCommand.BACKWARD_KILL_WORD);
            default -> Option.none();
        };
    }

    private static Option<LineCommand> ctrl(int key) {
        return switch (key) {
            case 'a' -> Option.of(LineCommand.BEGINNING_OF_LINE);
            case 'e' -> Option.of(LineCommand.END_OF_LINE);
            case 'w' -> Option.of(LineCommand.UNIX_WORD_RUBOUT);
            case 'u' -> Option.of(LineCommand.UNIX_LINE_DISCARD);
            case 'k' -> Option.of(LineCommand.KILL_LINE);
            default -> Option.none();
        };
    }

    private static Option<LineCommand> alt(int key) {
        return switch (key) {
            case 'b' -> Option.of(LineCommand.BACKWARD_WORD);
            case 'f' -> Option.of(LineCommand.FORWARD_WORD);
            case 'd' -> Option.of(LineCommand.KILL_WORD);
            default -> Option.none();
        };
    }

    /**
     * Edits {@code state} with {@code event}, if it is one of the keys.
     *
     * @return whether it was
     */
    public static boolean handle(TextInputState state, KeyEvent event) {
        return handle(new InputField(state), event);
    }

    /**
     * Edits {@code state} with {@code event}, if it is one of the keys.
     *
     * @return whether it was
     */
    public static boolean handle(TextAreaState state, KeyEvent event) {
        return handle(new AreaField(state), event);
    }

    private static boolean handle(Field field, KeyEvent event) {
        Option<LineCommand> command = command(event);
        command.forEach(it -> apply(field, it.on(new EditBuffer(field.text(), field.cursor()))));
        return command.isDefined();
    }

    /**
     * Makes {@code edit} with the field's own moves and deletes, so it keeps what goes with them, like where a text
     * area is scrolled to.
     */
    private static void apply(Field field, Edit edit) {
        switch (edit) {
            case Edit.Move(int to) -> moveTo(field, to);
            case Edit.Delete(int from, int to) -> {
                moveTo(field, to);
                deleteBackTo(field, from);
            }
        }
    }

    // These step once per character, so they loop rather than recurse: a pasted line can be long.

    /**
     * Moves the cursor of {@code field} to {@code to}, or just past it, as the field moves by whole characters.
     */
    private static void moveTo(Field field, int to) {
        int at = field.cursor();
        while (at < to) {
            field.right();
            int moved = field.cursor();
            if (moved == at) {
                return;
            }
            at = moved;
        }
        while (at > to) {
            field.left();
            int moved = field.cursor();
            if (moved == at) {
                return;
            }
            at = moved;
        }
    }

    private static void deleteBackTo(Field field, int from) {
        int at = field.cursor();
        while (at > from) {
            field.deleteBackward();
            int deleted = field.cursor();
            if (deleted == at) {
                return;
            }
            at = deleted;
        }
    }

    /**
     * The state of a field, as far as the commands need it.
     */
    private interface Field {
        String text();

        int cursor();

        void left();

        void right();

        void deleteBackward();
    }

    private record InputField(TextInputState state) implements Field {
        @Override
        public String text() {
            return state.text();
        }

        @Override
        public int cursor() {
            return state.cursorPosition();
        }

        @Override
        public void left() {
            state.moveCursorLeft();
        }

        @Override
        public void right() {
            state.moveCursorRight();
        }

        @Override
        public void deleteBackward() {
            state.deleteBackward();
        }
    }

    private record AreaField(TextAreaState state) implements Field {
        @Override
        public String text() {
            return state.text();
        }

        /**
         * The offset of the cursor in the text, each line before it counting its line break.
         */
        @Override
        public int cursor() {
            return List.range(0, state.cursorRow())
                            .map(row -> state.getLine(row).length() + 1)
                            .sum()
                            .intValue()
                    + state.cursorCol();
        }

        @Override
        public void left() {
            state.moveCursorLeft();
        }

        @Override
        public void right() {
            state.moveCursorRight();
        }

        @Override
        public void deleteBackward() {
            state.deleteBackward();
        }
    }
}
