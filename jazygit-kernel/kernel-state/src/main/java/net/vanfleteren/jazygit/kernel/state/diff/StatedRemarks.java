package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * The remarks a part states for a change whose files are listed (ADR 171): the kernel places and draws them, puts the
 * cursor's stops on them and lists the files with some, but keeps no copy of them; the part that read them does.
 *
 * @param threads     the threads, as they are read
 * @param atItsCommits whether they were read at the commits the files were read at, so that their lines number lines
 *                     of the diff shown: else the threads on a line are listed at the end of their file (ADR 55)
 * @param sending     the ids of the threads something is sent for, which take no key until it is done (ADR 60)
 * @param deleting    the ids of the remarks being deleted (ADR 64)
 * @param pictures    the images of the remarks read to be shown, by where they are: none while one is read, or when
 *                    it could not be (ADR 84)
 * @param ended       whether the change ended when its remarks were read last, and how: the files then stay at the
 *                    commits they were read at (ADR 55)
 */
public record StatedRemarks(
        Loadable<List<RemarkThread>> threads,
        boolean atItsCommits,
        Set<RemarkThread.Id> sending,
        Set<Remark.Id> deleting,
        Map<String, Option<Picture>> pictures,
        Option<Ended> ended) {

    /**
     * {@code threads}, read at the commits of the files, of a change that did not end, with nothing being sent or
     * deleted, and no pictures.
     */
    public static StatedRemarks of(List<RemarkThread> threads) {
        return new StatedRemarks(
                new Loadable.Loaded<>(threads), true, HashSet.empty(), HashSet.empty(), HashMap.empty(), Option.none());
    }

    /**
     * The threads read, once they are.
     */
    public List<RemarkThread> read() {
        return threads.toOption().getOrElse(List.empty());
    }

    /**
     * The threads that number lines of the diff shown: those read, when they were read at its commits.
     */
    public List<RemarkThread> onLines() {
        return atItsCommits ? read() : List.empty();
    }

    /**
     * How a change ended.
     */
    public enum Ended {
        MERGED,
        CLOSED
    }
}
