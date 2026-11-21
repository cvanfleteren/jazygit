package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.load.Targeted;

/**
 * The parts of the {@link Model} the features keep for themselves, by the name of their {@link Part} (ADR 157), each
 * with its part, so the kernel can ask them what it asks of a part (ADR 158). Only a part that differs from its
 * initial value is kept, and the kept ones are asked in the order of their names.
 *
 * @param kept each part that is kept, with its value, by its name
 */
public record Parts(Map<String, Kept<?>> kept) {

    public static final Parts NONE = new Parts(TreeMap.empty());

    /**
     * The value of {@code part}: its initial value while it is not kept.
     */
    @SuppressWarnings("unchecked") // Only with() keeps a value under a part's name, one of that part's type.
    public <S> S get(Part<S> part) {
        return kept.get(part.name()).map(value -> (S) value.value()).getOrElse(part.initial());
    }

    /**
     * These parts with {@code value} for {@code part}; not kept when that is its initial value.
     */
    public <S> Parts with(Part<S> part, S value) {
        return new Parts(
                value.equals(part.initial())
                        ? kept.remove(part.name())
                        : kept.put(part.name(), new Kept<>(part, value)));
    }

    /**
     * What the parts kept in {@code model} show, to read again.
     */
    public List<Targeted<?>> rereads(Model model) {
        return kept.values().toList().flatMap(part -> part.rereads(model));
    }

    /**
     * The commit {@code sha}, if a part kept lists it: the first that does, in the order of their names.
     */
    public Option<Commit> commit(Sha sha) {
        return kept.values().toStream().flatMap(part -> part.commit(sha)).headOption();
    }

    /**
     * What the parts kept state the branches are built on, in the order of their names, leaving out those that state
     * nothing (ADR 169).
     */
    public List<StatedBases> stated() {
        return kept.values().toList().map(Kept::bases).filter(bases -> !bases.equals(StatedBases.NONE));
    }

    /**
     * The repository on the host of the remotes, as {@code owner/name}: as the first part kept that knows it names it,
     * in the order of their names (ADR 169).
     */
    public Option<String> repository() {
        return kept.values().toStream().flatMap(Kept::repository).headOption();
    }

    /**
     * What {@code model} becomes once each part it keeps answered {@code event}, in the order of their names. Each
     * one answers with its value as the parts before it left it, and not at all once they stopped keeping it.
     */
    public static Update.Next reacting(Model model, KernelEvent event) {
        return model.parts()
                .kept
                .keySet()
                .toList()
                .foldLeft(
                        Update.Next.of(model),
                        (next, name) -> next.then(now -> now.parts()
                                .kept
                                .get(name)
                                .map(part -> part.reacting(now, event))
                                .getOrElse(() -> Update.Next.of(now))));
    }

    /**
     * A part that is kept, with its value.
     */
    public record Kept<S>(Part<S> part, S value) {

        List<Targeted<?>> rereads(Model model) {
            return part.hooks().rereads().of(model, value);
        }

        Option<Commit> commit(Sha sha) {
            return part.hooks().knowsCommit().commit(value, sha);
        }

        Update.Next reacting(Model model, KernelEvent event) {
            return part.hooks().reacting().to(model, value, event);
        }

        StatedBases bases() {
            return part.hooks().states().bases(value);
        }

        Option<String> repository() {
            return part.hooks().names().repository(value);
        }
    }
}
