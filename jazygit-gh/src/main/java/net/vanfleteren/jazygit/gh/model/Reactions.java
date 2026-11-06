package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;

/**
 * The reactions to a comment, in the order of {@link Reaction}, only those someone gave (ADR 86).
 *
 * @param groups each reaction given, with how often, and whether the viewer gave it
 */
public record Reactions(List<Group> groups) {

    /**
     * No reaction.
     */
    public static final Reactions NONE = new Reactions(List.empty());

    /**
     * Whether the viewer gave {@code reaction}.
     */
    public boolean viewerGave(Reaction reaction) {
        return groups.exists(group -> group.reaction() == reaction && group.viewer());
    }

    /**
     * These with {@code reaction} given by the viewer when {@code given}, else taken back, as GitHub counts them once
     * it took that: a reaction nobody gives any longer is left out. Giving one the viewer gave already, or taking back
     * one they did not, changes nothing, as GitHub does.
     */
    public Reactions withViewer(Reaction reaction, boolean given) {
        if (viewerGave(reaction) == given) {
            return this;
        }
        int delta = given ? 1 : -1;
        Group current = groups.find(group -> group.reaction() == reaction).getOrElse(new Group(reaction, 0, false));
        Group changed = new Group(reaction, current.count() + delta, given);
        return new Reactions(List.of(Reaction.values())
                .flatMap(each -> each == reaction
                        ? List.of(changed).filter(group -> group.count() > 0)
                        : groups.filter(group -> group.reaction() == each)));
    }

    /**
     * One reaction to a comment.
     *
     * @param reaction which
     * @param count    by how many
     * @param viewer   whether the viewer is one of them
     */
    public record Group(Reaction reaction, int count, boolean viewer) {}
}
