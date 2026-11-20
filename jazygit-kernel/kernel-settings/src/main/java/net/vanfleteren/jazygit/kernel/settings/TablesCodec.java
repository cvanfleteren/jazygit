package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * A list kept as an array of tables, one per entry (ADR 164): see {@link Codec#tables(TableCodec)}.
 *
 * @param entry how an entry is read from its table and kept in one
 * @param <E>   what an entry is
 */
record TablesCodec<E>(TableCodec<E> entry) implements Codec<Entries<E>> {

    @Override
    public boolean guarded() {
        return true;
    }

    @Override
    public Decoded<Entries<E>> decode(String key, Object value) {
        if (!(value instanceof java.util.List<?> array)
                || !array.stream().allMatch(item -> item instanceof java.util.Map<?, ?>)) {
            return new Decoded<>(Option.none(), List.of(key));
        }
        List<Tuple2<Entries.Entry<E>, List<String>>> read =
                List.ofAll(array).zipWithIndex().map(item -> read(key + "[" + (item._2 + 1) + "]", item._1));
        return new Decoded<>(Option.some(new Entries<>(read.map(Tuple2::_1))), read.flatMap(Tuple2::_2));
    }

    @Override
    public Map<List<String>, Object> encode(String key, Entries<E> entries) {
        return Codec.at(key, entries.all().map(this::written).toJavaList());
    }

    /**
     * The entry of {@code table}, the item at {@code at} of the array, with the keys of what is wrong in it: one the
     * app reads, keeping what it does not read and what is wrong, else one it cannot read, kept as written.
     */
    private Tuple2<Entries.Entry<E>, List<String>> read(String at, Object table) {
        LinkedHashMap<String, Object> fields = LinkedHashMap.ofAll(
                ((java.util.Map<?, ?>) table).entrySet().stream(),
                field -> Tuple.<String, Object>of(String.valueOf(field.getKey()), field.getValue()));
        Decoded<E> decoded = entry.decode(at, fields);
        Entries.Entry<E> read = decoded.value()
                .<Entries.Entry<E>>map(found -> new Entries.Entry.Valid<>(
                        found,
                        fields.filterKeys(field -> !entry.fields().contains(field)
                                || decoded.invalid().contains(at + "." + field))))
                .getOrElse(() -> new Entries.Entry.Invalid<>(fields, decoded.invalid()));
        return Tuple.of(read, decoded.invalid());
    }

    /**
     * How {@code read} is kept in the array: the table of what it holds, then what its table had besides, unless it
     * holds a value there now; one the app cannot read, as it was written.
     */
    private Object written(Entries.Entry<E> read) {
        return switch (read) {
            case Entries.Entry.Invalid<E> invalid -> plain(invalid.table());
            case Entries.Entry.Valid<E> valid -> {
                LinkedHashMap<String, Object> known = entry.encode(valid.read());
                yield plain(known.merge(valid.unknown().filterKeys(field -> !known.containsKey(field))));
            }
        };
    }

    private static Object plain(LinkedHashMap<String, Object> table) {
        return table.toJavaMap(java.util.LinkedHashMap::new, field -> field);
    }
}
