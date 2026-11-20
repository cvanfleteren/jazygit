package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.List;

/**
 * The other entries of a list, that the entry the form edits is checked against (ADR 164): those before it and those
 * after it, as the order of a list can decide which of two entries holds what both claim, as a shortcut. A new entry
 * goes at the end, so all are before it.
 *
 * @param before the entries before it, in their order
 * @param after  the entries after it, in their order
 * @param <E>    what an entry is
 */
public record Others<E>(List<E> before, List<E> after) {

    /**
     * Others that are all before the entry, as for a new one.
     */
    public static <E> Others<E> before(List<E> entries) {
        return new Others<>(entries, List.empty());
    }

    /**
     * All of them, in their order.
     */
    public List<E> all() {
        return before.appendAll(after);
    }
}
