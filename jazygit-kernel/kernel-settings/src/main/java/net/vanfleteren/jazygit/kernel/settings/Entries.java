package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The entries of a list the settings panel edits, in their order (ADR 164): each one the app could read, with what
 * its table had that the app does not know, and each one it could not, kept as the file had it. Saving writes them
 * all back, so neither a newer jazygit's field nor an entry the user still has to fix is lost.
 *
 * @param all the entries, in the order of their tables in the file
 * @param <E> what an entry is
 */
public record Entries<E>(List<Entry<E>> all) {

    /**
     * No entries.
     */
    public static <E> Entries<E> none() {
        return new Entries<>(List.empty());
    }

    /**
     * The entries {@code values}, each with nothing the app does not know.
     */
    public static <E> Entries<E> of(List<E> values) {
        return new Entries<>(values.map(Entry.Valid::of));
    }

    /**
     * The entries the app could read, in their order.
     */
    public List<E> values() {
        return all.flatMap(Entry::value);
    }

    /**
     * These entries with {@code value} at the end.
     */
    public Entries<E> added(E value) {
        return new Entries<>(all.append(Entry.Valid.of(value)));
    }

    /**
     * These entries with {@code value} in place of the one at {@code index}, which keeps what its table had that the
     * app does not know, but not what it had wrong at a key the app reads: {@code value} sets that key now, or leaves
     * it out; as they are when there is none there.
     */
    public Entries<E> replaced(int index, E value) {
        return at(index)
                .map(entry -> new Entries<>(all.update(index, new Entry.Valid<>(value, entry.unknown()))))
                .getOrElse(this);
    }

    /**
     * These entries without the one at {@code index}.
     */
    public Entries<E> removed(int index) {
        return at(index).isDefined() ? new Entries<>(all.removeAt(index)) : this;
    }

    /**
     * These entries with the one at {@code index} moved {@code by} places, up when negative; as they are when it
     * would leave the list.
     */
    public Entries<E> moved(int index, int by) {
        int to = index + by;
        return at(index).isDefined() && at(to).isDefined()
                ? new Entries<>(all.removeAt(index).insert(to, all.get(index)))
                : this;
    }

    /**
     * The entry at {@code index}, if there is one.
     */
    public Option<Entry<E>> at(int index) {
        return Option.when(index >= 0 && index < all.size(), () -> all.get(index));
    }

    /**
     * An entry of a list.
     */
    public sealed interface Entry<E> {

        /**
         * What it holds, if the app could read it.
         */
        Option<E> value();

        /**
         * What its table had that the app does not read, by key.
         */
        LinkedHashMap<String, Object> unknown();

        /**
         * An entry the app could read.
         *
         * @param read    what it holds
         * @param unknown what its table had at keys the app does not read, written back with it
         * @param wrong   what its table had that was wrong at keys the app reads, left out of {@code read}; written
         *                back with it until the entry is replaced, as the form then sets those keys, or leaves them
         *                out
         */
        record Valid<E>(E read, LinkedHashMap<String, Object> unknown, LinkedHashMap<String, Object> wrong)
                implements Entry<E> {

            /**
             * An entry the app could read, with nothing wrong in its table.
             */
            public Valid(E read, LinkedHashMap<String, Object> unknown) {
                this(read, unknown, LinkedHashMap.empty());
            }

            public static <E> Valid<E> of(E read) {
                return new Valid<>(read, LinkedHashMap.empty());
            }

            @Override
            public Option<E> value() {
                return Option.of(read);
            }
        }

        /**
         * An entry the app could not read: the panel shows what is wrong in it, and saving writes it back as it was.
         *
         * @param table its table, as the file has it
         * @param wrong the keys of what is wrong in it, such as {@code agents[2].command}
         */
        record Invalid<E>(LinkedHashMap<String, Object> table, List<String> wrong) implements Entry<E> {

            @Override
            public Option<E> value() {
                return Option.none();
            }

            @Override
            public LinkedHashMap<String, Object> unknown() {
                return LinkedHashMap.empty();
            }
        }
    }
}
