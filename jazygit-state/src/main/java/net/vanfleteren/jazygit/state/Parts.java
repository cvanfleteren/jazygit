package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;

/**
 * The parts of the {@link Model} the features keep for themselves, by their {@link Part} (ADR 157). Only a part that
 * differs from its initial value is kept.
 *
 * @param values the value of each part that is kept
 */
public record Parts(Map<Part<?>, Object> values) {

    public static final Parts NONE = new Parts(HashMap.empty());

    /**
     * The value of {@code part}: its initial value while it is not kept.
     */
    @SuppressWarnings("unchecked") // Only with() puts a value under a part, and only one of its type.
    public <S> S get(Part<S> part) {
        return values.get(part).map(value -> (S) value).getOrElse(part.initial());
    }

    /**
     * These parts with {@code value} for {@code part}; not kept when that is its initial value.
     */
    public <S> Parts with(Part<S> part, S value) {
        return new Parts(value.equals(part.initial()) ? values.remove(part) : values.put(part, value));
    }
}
