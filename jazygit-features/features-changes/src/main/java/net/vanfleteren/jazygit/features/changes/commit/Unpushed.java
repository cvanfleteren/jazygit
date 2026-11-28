package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * Which commits of the log of HEAD are not pushed to the upstream of the checked out branch yet, so that their shas
 * stand out in the commits pane (#722). Commits the main branch has count as pushed: others have them.
 *
 * @param asked   the tips last asked about, read or still being read
 * @param read    the tips {@code commits} were read for
 * @param commits the commits HEAD has and none of the tips of {@code read} has
 */
@With
public record Unpushed(Option<Tips> asked, Option<Tips> read, Set<Sha> commits) {

    public static final Unpushed NONE = new Unpushed(Option.none(), Option.none(), HashSet.empty());

    public static final Part<Unpushed> PART = Part.of("changes.unpushed", NONE);

    /**
     * The commits not pushed, when they were read for {@code tips}; else none, rather than those of other tips.
     */
    public Set<Sha> of(Tips tips) {
        return read.contains(tips) ? commits : HashSet.empty();
    }

    /**
     * What the commits not pushed are read against: HEAD, and the tips of its upstream and of the main branch on
     * {@code origin}, if that is not the branch checked out.
     *
     * @param head   the commit HEAD is, the first of the log
     * @param shared the tips whose commits count as pushed, the upstream's first
     */
    public record Tips(Sha head, List<Sha> shared) {}
}
