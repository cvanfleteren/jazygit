package net.vanfleteren.jazygit.changes.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

public sealed interface VersionMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return VersionUpdate.update(model, this);
    }

    /**
     * The user asked to show the next of the diff, the new version and the original of the one file of the area the
     * cursor {@code at} is in (ADR 102).
     */
    record Cycled(DiffCursorAt at) implements VersionMsg {}

    /**
     * The user asked to show the version of the one file of the area the cursor {@code at} is in rendered, or as source
     * again (ADR 103).
     */
    record RenderToggled(DiffCursorAt at) implements VersionMsg {}

    /**
     * The user asked, in the files pane, to show the next of the diff, the new version and the original of the file
     * highlighted, in every area the content panel shows it in: where the unstaged area goes, the staged one goes too.
     */
    record FilesCycled() implements VersionMsg {}

    /**
     * The user asked, in the files pane, to show the file highlighted rendered, or as source again, in every area the
     * content panel shows it in, as the unstaged area goes.
     */
    record FilesRenderToggled() implements VersionMsg {}

    /**
     * The user asked to show who last changed each line of the version of the one file of the area the cursor
     * {@code at} is in, or to stop to (ADR 147).
     */
    record BlameToggled(DiffCursorAt at) implements VersionMsg {}

    /**
     * The user asked, in the files pane, to show who last changed each line of the version of the file highlighted,
     * or to stop to, in every area the content panel shows it in, as the unstaged area goes.
     */
    record FilesBlameToggled() implements VersionMsg {}

    /**
     * The user asked, on the line the cursor {@code at} is on, to open the files of the commit it is blamed on, if the
     * version of the area is shown blamed (ADR 147).
     */
    record LineOpened(DiffCursorAt at) implements VersionMsg {}
}
