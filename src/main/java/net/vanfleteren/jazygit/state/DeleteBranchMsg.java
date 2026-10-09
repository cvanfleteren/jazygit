package net.vanfleteren.jazygit.state;

public sealed interface DeleteBranchMsg extends Msg {

    /**
     * The user asked to delete a branch; where is still to be chosen.
     */
    record Requested(String branch) implements DeleteBranchMsg {
    }

    record Cancelled() implements DeleteBranchMsg {
    }

    record Chosen(Cmd.BranchCmd.DeleteBranch.DeleteScope scope) implements DeleteBranchMsg {
    }

    record Deleted(String branch) implements DeleteBranchMsg {
    }

    record Failed(String branch, String message) implements DeleteBranchMsg {
    }
}
