package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;

/**
 * A remark of a {@link RemarkThread}, as a forge states it (ADR 171).
 *
 * @param id        its id, opaque: written by the forge, only compared by the kernel
 * @param author    who wrote it, empty when the forge does not say, as for a deleted account
 * @param at        when it was written
 * @param body      its text, in Markdown
 * @param hidden    why it is hidden, as a word (outdated, resolved, off-topic, spam…), if it is
 * @param reactions the reactions given to it, in the order they are shown
 */
public record Remark(Id id, String author, Instant at, String body, Option<String> hidden, List<Reaction> reactions) {

    /**
     * A remark nobody reacted to.
     */
    public Remark(Id id, String author, Instant at, String body, Option<String> hidden) {
        this(id, author, at, body, hidden, List.empty());
    }

    /**
     * The id of a remark, opaque.
     *
     * @param value the id
     */
    public record Id(String value) {}

    /**
     * A reaction given to a remark: {@code emoji}, given {@code count} times, of which once by the user when
     * {@code given}.
     */
    public record Reaction(String emoji, int count, boolean given) {}
}
