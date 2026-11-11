package net.vanfleteren.jazygit.state.index;

import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.state.Jobs;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * When the index is written anew (ADR 97): once it changed and settled, as after a checkout, and every
 * {@link #FALLBACK} anyway, for files that were touched without the index changing.
 */
public final class IndexUpdate {

    /**
     * How long the index waits at most to be written anew, when nothing told it changed: a file a tool touched
     * without changing it does not change the index.
     */
    static final Duration FALLBACK = Duration.ofMinutes(5);

    private IndexUpdate() {}

    /**
     * {@code next}, which handled the stamps that {@code CheckMetadata} took, also writing the index anew if it
     * looks other than {@code index} since it was last written anew, which the first stamp that can be told always does,
     * or that was {@link #FALLBACK} ago. Only while the
     * terminal has the focus, when the user is not likely to run git elsewhere, and while no operation of the app
     * runs, as a refresh that has something to write locks the index a moment. The stamp of the index is none while
     * it was written too recently to tell a later change apart, so it is written anew once it settled.
     */
    public static Next checked(Next next, Option<MetadataStamp> index) {
        Model model = next.model();
        Jobs jobs = model.jobs();
        IndexRefresh last = adopted(jobs.indexRefresh(), index);
        boolean changed = index.isDefined() && !last.seen().equals(index);
        boolean due = changed || last.at().exists(at -> !model.now().isBefore(at.plus(FALLBACK)));
        boolean free =
                !jobs.away() && !last.adopt() && !jobs.runsOperation() && !jobs.runs(RefreshIndex.class::isInstance);
        return due && free
                ? new Next(
                        model.mapJobs(those ->
                                those.withIndexRefresh(new IndexRefresh(index, Option.of(model.now()), false))),
                        next.cmds().append(new RefreshIndex()),
                        next.quit())
                : new Next(model.mapJobs(those -> those.withIndexRefresh(last)), next.cmds(), next.quit());
    }

    /**
     * {@code model} once a refresh is done: one that {@code written} the index leaves it looking as the next stamp
     * that can be told says, which is then no change to write anew again. One that could not, as another command
     * held the index, is tried again once the index changes, or it is time to anyway.
     */
    static Next refreshed(Model model, boolean written) {
        return Next.of(
                model.mapJobs(jobs -> jobs.withIndexRefresh(jobs.indexRefresh().withAdopt(written))));
    }

    /**
     * {@code last}, taking {@code index} as what a refresh that just wrote the index left, once it can be told.
     */
    private static IndexRefresh adopted(IndexRefresh last, Option<MetadataStamp> index) {
        return last.adopt() && index.isDefined() ? new IndexRefresh(index, last.at(), false) : last;
    }
}
