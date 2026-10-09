package net.vanfleteren.jazygit.model;

import java.util.List;

/**
 * Source of the git information shown by the TUI. Every call reads the current state of the
 * repository; deciding when to call is up to the caller.
 */
public interface GitInfoProvider {

    /**
     * The name of the repository, i.e. the name of its working tree directory.
     */
    String repositoryName();

    RepoStatus status();

    List<Branch> branches();

    List<Commit> commits();
}
