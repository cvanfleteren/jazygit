package net.vanfleteren.jazygit.kernel.state.load;

import net.vanfleteren.jazygit.core.git.model.DiffOptions;

/**
 * A load whose result depends on the diff options it was started with.
 */
public sealed interface DiffLoad<T> extends Targeted<T>
        permits LoadCommitDetail, LoadStashDetail, LoadCommitFileDiff, LoadFileDiff, ReadWholeFile {

    DiffOptions options();
}
