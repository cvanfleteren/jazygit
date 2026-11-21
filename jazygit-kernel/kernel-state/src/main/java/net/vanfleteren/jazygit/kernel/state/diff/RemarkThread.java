package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A thread of remarks on a change, about a line of a file of its diff, or about a whole file, as a forge states it for
 * the kernel to place and draw (ADR 55, 171). It holds nothing of the forge's own: its ids are opaque, written by the
 * forge and only compared by the kernel.
 *
 * @param id           its id, opaque
 * @param path         the path of its file, as it is in the newer commit of the change
 * @param origin       the path its file had before, when the forge knows it was renamed
 * @param subject      whether it is about a line or a whole file
 * @param line         the number of its line, its last when it is about several; none when it is outdated, or about
 *                     a whole file
 * @param side         on which side of the diff its line is
 * @param startLine    the number of its first line when it is about several
 * @param startSide    on which side of the diff its first line is, when it is about several, which may be the other
 *                     one than that of its last
 * @param originalLine the number its line had when the thread was made, in the commit it was made on
 * @param outdated     whether its line changed since it was made
 * @param resolved     whether it is resolved
 * @param more         how many more remarks it has than were read
 * @param remarks      its remarks, oldest first, as far as they were read
 */
public record RemarkThread(
        Id id,
        String path,
        Option<String> origin,
        Subject subject,
        Option<Integer> line,
        DiffSide side,
        Option<Integer> startLine,
        Option<DiffSide> startSide,
        Option<Integer> originalLine,
        boolean outdated,
        boolean resolved,
        int more,
        List<Remark> remarks) {

    /**
     * Whether it is on the file with the path {@code path}, renamed from {@code origin} if it was: a forge may give the
     * new path of a renamed file, but git may pair it with another than the forge does, and the old path of a deleted
     * one, which is also its path in git (ADR 55). Wherever a thread is matched to a file, it is matched by this, also
     * where the threads of each file are found by their path ({@link RemarksByPath#on}).
     */
    public boolean isOn(String path, Option<String> origin) {
        return path.equals(this.path) || origin.contains(this.path) || this.origin.contains(path);
    }

    /**
     * The side of its first line: that of its last, unless it says otherwise.
     */
    public DiffSide firstSide() {
        return startSide.getOrElse(side);
    }

    /**
     * This thread with {@code remarks} in place of its own.
     */
    public RemarkThread withRemarks(List<Remark> remarks) {
        return new RemarkThread(
                id,
                path,
                origin,
                subject,
                line,
                side,
                startLine,
                startSide,
                originalLine,
                outdated,
                resolved,
                more,
                remarks);
    }

    /**
     * The id of a thread, opaque: written by the forge, only compared by the kernel.
     *
     * @param value the id
     */
    public record Id(String value) {}

    /**
     * What a thread is about.
     */
    public enum Subject {
        LINE,
        FILE
    }
}
