package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;

/**
 * The parts of the {@link Model} the features keep for themselves, by the name of their {@link Part} (ADR 157). Only a
 * part that differs from its initial value is kept.
 *
 * @param values the value of each part that is kept, by its name
 */
public record Parts(Map<String, Object> values) {

    public static final Parts NONE = new Parts(HashMap.empty());

    /**
     * The value of {@code part}: its initial value while it is not kept.
     */
    @SuppressWarnings("unchecked") // Only with() puts a value under a part's name, one of that part's type.
    public <S> S get(Part<S> part) {
        return values.get(part.name()).map(value -> (S) value).getOrElse(part.initial());
    }

    /**
     * These parts with {@code value} for {@code part}; not kept when that is its initial value.
     */
    public <S> Parts with(Part<S> part, S value) {
        return new Parts(value.equals(part.initial()) ? values.remove(part.name()) : values.put(part.name(), value));
    }
}
