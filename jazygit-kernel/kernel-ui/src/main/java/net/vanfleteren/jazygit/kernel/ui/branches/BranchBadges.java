package net.vanfleteren.jazygit.kernel.ui.branches;

import io.vavr.collection.List;
import java.time.Instant;
import java.util.ServiceLoader;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * What the features show of a branch in the branches pane, beside what git says of it (ADR 167): the badges of a row,
 * after its parent and before whether it is checked out in another worktree. The local and the remote branches share
 * them. The pane knows no feature: the app gives the badges of its features, in their order, as a service, as it gives
 * the keymap (ADR 136).
 *
 * @param badges what each feature shows of a branch, in the order of the features
 */
public record BranchBadges(List<Badge> badges) {

    /**
     * No badges, as the tests of the views have them without the app.
     */
    public static final BranchBadges NONE = new BranchBadges(List.empty());

    /**
     * What the features show of {@code branch} in {@code model}: their segments in their order, and their texts that
     * change with the time, apart.
     */
    public Shown of(Model model, BranchRef branch) {
        List<Shown> each = badges.map(badge -> badge.of(model, branch));
        return new Shown(
                each.flatMap(Shown::segs),
                now -> each.map(shown -> shown.timed().apply(now))
                        .filter(text -> !text.isEmpty())
                        .mkString(" "));
    }

    /**
     * The badges of the app, as declared by its features: those of the {@link Provider} on the classpath. Without the
     * app, as in the tests of the views, there are {@link #NONE}.
     */
    public static BranchBadges standard() {
        return Found.BADGES;
    }

    /**
     * What a feature shows of a branch.
     */
    @FunctionalInterface
    public interface Badge {

        /**
         * What the feature shows of {@code branch} in {@code model}; {@link Shown#NONE} for nothing.
         */
        Shown of(Model model, BranchRef branch);
    }

    /**
     * What a row shows of a branch: its segments at the time of the model, and the text of them that changes with the
     * time, at a given time, which the pane adds to the age of the branch to know when to draw the row again (ADR 124).
     *
     * @param segs  the segments, each with a leading space
     * @param timed the text of the segments that changes with the time, at that time; empty when none does
     */
    public record Shown(List<LoadableList.Seg> segs, Function<Instant, String> timed) {

        /**
         * Nothing shown.
         */
        public static final Shown NONE = new Shown(List.empty(), now -> "");

        /**
         * {@code segs}, none of which changes with the time.
         */
        public static Shown of(List<LoadableList.Seg> segs) {
            return new Shown(segs, now -> "");
        }
    }

    /**
     * Gives the badges of the app's features, as a service.
     */
    public interface Provider {

        BranchBadges badges();
    }

    private static final class Found {

        private static final BranchBadges BADGES = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::badges)
                .orElse(NONE);
    }
}
