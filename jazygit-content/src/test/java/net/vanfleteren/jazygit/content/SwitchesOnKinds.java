package net.vanfleteren.jazygit.content;

import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.ui.content.Body;

/**
 * Tells kinds of content apart by a pattern switch, as nothing in jazygit-content may: what
 * {@code ContentArchitectureTest} is checked to catch.
 */
final class SwitchesOnKinds {

    private SwitchesOnKinds() {}

    static boolean isNote(ScrolledContent content) {
        return switch (content) {
            case FileNote.Note note -> true;
            default -> false;
        };
    }

    static boolean isBuilds(Body body) {
        return switch (body) {
            case BuildsBody builds -> true;
            default -> false;
        };
    }
}
