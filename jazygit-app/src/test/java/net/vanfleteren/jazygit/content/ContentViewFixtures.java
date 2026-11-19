package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.content.ShownDiff;
import net.vanfleteren.jazygit.ui.content.Tone;

/**
 * What the tests of {@link ContentView} share: the messages it shows and the models it shows them in.
 */
final class ContentViewFixtures {

    private ContentViewFixtures() {}

    static final DiffLook PLAIN_LOOK = new DiffLook(DiffStyle.PLAIN, true, true);

    /**
     * What the files pane says in place of a diff: a note, an area that holds the focus (ADR 117).
     */
    static Shown note(String title, String text, Tone tone) {
        return new Shown.Area(FileNote.AREA, title, new FileNote.Note(text), new NoteBody(text, tone));
    }

    static Shown note(String title, String text) {
        return note(title, text, Tone.DIM);
    }

    /**
     * {@code diff} shown as it is, whole.
     */
    static ShownDiff whole(String diff) {
        return new ShownDiff(DiffText.of(diff), Option.none());
    }
}
