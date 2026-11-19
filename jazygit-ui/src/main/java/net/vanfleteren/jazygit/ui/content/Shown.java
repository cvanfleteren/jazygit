package net.vanfleteren.jazygit.ui.content;

import io.vavr.collection.List;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.ui.text.Placeholders;

/**
 * What the content panel shows: a message, or areas with something to scroll through.
 */
public sealed interface Shown {

    /**
     * A message titled {@code title} saying that what it shows is still read.
     */
    static Shown loading(String title) {
        return dim(title, Placeholders.loading());
    }

    /**
     * A message titled {@code title} saying {@code text} dimmed, as what is not there.
     */
    static Shown dim(String title, String text) {
        return new Message(title, text, Tone.DIM);
    }

    /**
     * What {@code view} makes of {@code loadable} once it is read, titled {@code title}; while it is read, or when it
     * could not be, a message that says so.
     */
    static <T> Shown whenLoaded(String title, Loadable<T> loadable, Function<T, Shown> view) {
        return switch (loadable) {
            case Loadable.Loaded<T>(T value) -> view.apply(value);
            case Loadable.Loading<T>() -> loading(title);
            case Loadable.Failed<T>(Failure failure) -> new Message(title, Placeholders.error(failure), Tone.ERROR);
        };
    }

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
