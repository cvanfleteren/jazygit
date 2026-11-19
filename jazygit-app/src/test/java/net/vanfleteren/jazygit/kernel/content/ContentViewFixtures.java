package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.ShownDiff;
import net.vanfleteren.jazygit.kernel.ui.content.Tone;

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
