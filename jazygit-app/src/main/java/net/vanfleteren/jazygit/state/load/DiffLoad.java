package net.vanfleteren.jazygit.state.load;

import net.vanfleteren.jazygit.git.model.DiffOptions;

/**
 * A load whose result depends on the diff options it was started with.
 */
public sealed interface DiffLoad extends Load
        permits LoadCommitDetail, LoadStashDetail, LoadCommitFileDiff, LoadFileDiff, ReadWholeFile {

    DiffOptions options();
}
