package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;

/**
 * How one entry of a list the panel edits is read from a table of the settings file and kept in one (ADR 164), as an
 * agent is from a table of {@code [[agents]]}.
 *
 * @param <E> what an entry is
 */
public interface TableCodec<E> {

    /**
     * The keys of the table it reads; what the table has at other keys is kept with the entry, and written back.
     */
    List<String> fields();

    /**
     * What {@code table}, the entry at {@code at} such as {@code agents[2]}, holds, with the keys of what is wrong in
     * it, such as {@code agents[2].command}. Only the {@link #fields()} are read.
     */
    Decoded<E> decode(String at, LinkedHashMap<String, Object> table);

    /**
     * How {@code entry} is kept in a table: a value for each of the {@link #fields()} it has, in their order. A value
     * is as the file has it: text, a number, a boolean, a {@link java.util.List} for an array.
     */
    LinkedHashMap<String, Object> encode(E entry);
}
