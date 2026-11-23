package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Either;
import lombok.With;
import net.vanfleteren.jazygit.core.git.model.DiffFilter;

/**
 * The diff filters to choose from, those that come with jazygit and the user's, each as it can be used or why it
 * cannot, and whether it is enabled (ADR 144).
 *
 * @param all the filters, built-in ones first, in their order
 * @param off whether all are switched off for this session, whichever are enabled
 */
@With
public record DiffFilters(List<Choice> all, boolean off) {

    /**
     * No filter to choose from.
     */
    public static final DiffFilters NONE = new DiffFilters(List.empty(), false);

    /**
     * The filters of {@code user}'s settings file after those that come with jazygit, each replacing the built-in one of
     * its id, with those whose ids {@code enabled} lists enabled, and all off when {@code off}.
     */
    public static DiffFilters of(FilterDefinitions user, List<String> enabled, boolean off) {
        return new DiffFilters(
                user.withBuiltIn()
                        .all()
                        .map(definition -> new Choice(
                                definition,
                                DiffFilter.of(
                                        definition.id(),
                                        definition.name(),
                                        definition.languages(),
                                        definition.paths(),
                                        definition.lines()),
                                enabled.contains(definition.id()))),
                off);
    }

    /**
     * The filters that hide hunks now: those enabled that can be used, unless all are switched off.
     */
    public List<DiffFilter> active() {
        return off ? List.empty() : enabledFilters();
    }

    /**
     * The filters enabled that can be used, whether or not all are switched off.
     */
    public List<DiffFilter> enabledFilters() {
        return all.filter(Choice::enabled).flatMap(choice -> choice.filter().toOption());
    }

    /**
     * A filter to choose from.
     *
     * @param definition what it is as written
     * @param filter     the filter, or why it cannot be used
     * @param enabled    whether its id is among those enabled
     */
    public record Choice(FilterDefinition definition, Either<DiffFilter.Problem, DiffFilter> filter, boolean enabled) {

        /**
         * Its name: the one it was given, else its id.
         */
        public String name() {
            return definition.name().getOrElse(definition.id());
        }
    }
}
