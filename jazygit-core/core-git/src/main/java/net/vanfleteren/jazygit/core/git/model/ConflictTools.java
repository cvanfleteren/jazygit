package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.RepoPath;

/**
 * What a file in conflict can be handed to in a repository (ADR 201), and the command lines that hand it, which the
 * commands run and the resolve dialog shows, so the two cannot differ.
 *
 * @param mergeTool  whether git has a merge tool set, {@code merge.tool}, which {@code git mergetool} runs
 * @param backupSet  whether the user set {@code mergetool.keepBackup}, either way, which is then left as it is
 * @param mergiraf   whether a program {@code mergiraf} runs
 */
public record ConflictTools(boolean mergeTool, boolean backupSet, boolean mergiraf) {

    /**
     * Nothing to hand a file to, as when what the repository has could not be read.
     */
    public static final ConflictTools NONE = new ConflictTools(false, false, false);

    /**
     * Whether the file in {@code conflict} can go to the merge tool: when there is one, and both sides changed it, as
     * for a side that deleted it git would ask in the terminal whether to keep it.
     */
    public boolean mergesWithTool(Conflict conflict) {
        return mergeTool && changedOnBothSides(conflict);
    }

    /**
     * Whether the file in {@code conflict} can go to mergiraf: when it runs, and both sides changed it.
     */
    public boolean solvesWithMergiraf(Conflict conflict) {
        return mergiraf && changedOnBothSides(conflict);
    }

    /**
     * {@code git mergetool -- <path>}, which keeps no {@code .orig} backup unless the user said whether to.
     */
    public List<String> mergeTool(RepoPath path) {
        List<String> backup = backupSet ? List.empty() : List.of("-c", "mergetool.keepBackup=false");
        return List.of("git").appendAll(backup).appendAll(List.of("mergetool", "--", path.value()));
    }

    /**
     * {@code path} solved by mergiraf in place, without a backup: when {@code rewrite}, after its conflict is written
     * again with its base, which mergiraf reads; else as it is, as when it has its bases already (ADR 201).
     */
    public static List<List<String>> mergiraf(RepoPath path, boolean rewrite) {
        List<String> solve = List.of("mergiraf", "solve", "--keep-backup=false", path.value());
        return rewrite
                ? List.of(List.of("git", "checkout", "--conflict=diff3", "--", path.value()), solve)
                : List.of(solve);
    }

    private static boolean changedOnBothSides(Conflict conflict) {
        return conflict.ours() == Conflict.Side.UPDATED && conflict.theirs() == Conflict.Side.UPDATED;
    }
}
