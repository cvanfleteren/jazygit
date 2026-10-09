package net.vanfleteren.jazygit.state;


/**
 * Everything that can happen to the {@link Model}, grouped by feature. Each feature has its own
 * sub-interface, and its own part of {@link Update}.
 */
public sealed interface Msg
        permits Msg.Tick, LoadMsg, SelectionMsg, CheckoutMsg, NewBranchMsg, DeleteBranchMsg, StageMsg,
        CommitMsg, AmendMsg, RewordMsg {

    /**
     * Periodic prompt to check the repository for changes made outside the app.
     */
    record Tick() implements Msg {
    }
}
