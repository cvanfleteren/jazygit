package net.vanfleteren.jazygit.ui.content;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;

/**
 * What the content panel shows: a message, or areas with something to scroll through.
 */
public sealed interface Shown {

    /**
     * The areas shown, top to bottom; none for a message.
     */
    default List<Area> areas() {
        return switch (this) {
            case Message _ -> List.empty();
            case Area area -> List.of(area);
            case Stacked(List<Area> areas) -> areas;
        };
    }

    /**
     * A panel titled {@code title} with only {@code text}: what is still loading, failed or is not there.
     */
    record Message(String title, String text, Tone tone) implements Shown {}

    /**
     * The area {@code area}, titled {@code title}, showing {@code body}; where it is scrolled to holds for
     * {@code content}.
     */
    record Area(ContentArea area, String title, ScrolledContent content, Body body) implements Shown {}

    /**
     * The areas one above the other.
     */
    record Stacked(List<Area> areas) implements Shown {}
}
