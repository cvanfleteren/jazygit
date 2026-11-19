package net.vanfleteren.jazygit.kernel.content;

import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.Tone;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;

/**
 * What the files pane shows in place of a diff or a file's content, such as that a file is binary, or still read: a
 * note, in an area that holds the focus while the files are walked from the content panel (ADR 117).
 */
final class FileNote {

    /**
     * The area of the note, which the focus is not moved into from the files pane.
     */
    static final ContentArea AREA = ContentArea.of(AreaId.FILE_NOTE, FilesPanel.PANE);

    private FileNote() {}

    /**
     * {@code shown}, a message in it as a note.
     */
    static Shown noted(Shown shown) {
        return switch (shown) {
            case Shown.Message(String title, String text, Tone tone) ->
                new Shown.Area(AREA, title, new Note(text), new NoteBody(text, tone));
            case Shown other -> other;
        };
    }

    /**
     * What the files pane says in place of a diff or a file's content, {@code text}, as an area keeps where it is
     * scrolled to for it (ADR 117).
     */
    record Note(String text) implements ScrolledContent.Other {}
}
