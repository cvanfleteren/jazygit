package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.core.git.model.Blame;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.kernel.state.Loadable;

/**
 * The blame of each version of a file read, all read alike (ADR 147). A version not in {@code read} is not blamed yet.
 *
 * @param ignoreWhitespace whether they are read ignoring whitespace, as the diff options had it when they were
 * @param read             the blame of each version read, being read, or that failed to be
 */
public record Blames(boolean ignoreWhitespace, Map<FileVersion, Loadable<Blame>> read) {

    /**
     * No version blamed yet, to be read ignoring whitespace or not.
     */
    public static Blames none(boolean ignoreWhitespace) {
        return new Blames(ignoreWhitespace, HashMap.empty());
    }

    /**
     * These blames, if they are read ignoring whitespace as {@code ignore} says; else none, as the blames read the
     * other way blame by a whitespace the user turned off, or on.
     */
    public Blames readIgnoring(boolean ignore) {
        return ignore == ignoreWhitespace ? this : none(ignore);
    }

    /**
     * These blames with the blame of {@code version} as {@code blame}.
     */
    public Blames with(FileVersion version, Loadable<Blame> blame) {
        return new Blames(ignoreWhitespace, read.put(version, blame));
    }

    /**
     * These blames without that of {@code version}, so that it is read again.
     */
    public Blames without(FileVersion version) {
        return new Blames(ignoreWhitespace, read.remove(version));
    }
}
