package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;

/**
 * The search of the content panel (ADR 126): one for the panel, whichever area shows it, kept while other items are
 * highlighted until it is cleared.
 *
 * @param text   the query as typed; empty only while the line to type it in is open
 * @param before the query from before the line was opened, empty when there was none, while the line is open
 */
public record ContentSearch(String text, Option<String> before) {

    /**
     * The search {@code current} with the line to type it in opened, showing its query.
     */
    public static ContentSearch opened(Option<ContentSearch> current) {
        String text = current.map(ContentSearch::text).getOrElse("");
        return new ContentSearch(text, Option.of(text));
    }

    /**
     * Whether the line to type the query in is open: nothing is searched while it is (ADR 126).
     */
    public boolean typing() {
        return before.isDefined();
    }

    /**
     * What is searched for: nothing while the line is open, or holds no text.
     */
    public Option<String> query() {
        return Option.when(!typing() && !text.isEmpty(), text);
    }

    /**
     * What {@code search} searches for, if there is one.
     */
    public static Option<String> query(Option<ContentSearch> search) {
        return search.flatMap(ContentSearch::query);
    }
}
