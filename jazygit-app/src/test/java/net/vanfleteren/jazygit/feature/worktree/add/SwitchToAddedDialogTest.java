package net.vanfleteren.jazygit.feature.worktree.add;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The popup asking to switch to the worktree just added: its path on a line of its own, from {@code ~} in the home
 * directory, and the question below it after a blank line.
 */
class SwitchToAddedDialogTest {

    @Test
    void showsThePathOnALineOfItsOwnFromATilde() {
        SwitchToAddedPopup popup =
                new SwitchToAddedPopup("feature", System.getProperty("user.home") + "/repos/app-feature");

        assertEquals(
                "The worktree was added at\n~/repos/app-feature\n\nSwitch to it?",
                AddWorktreeDialogs.SWITCH_TO_ADDED.message().apply(popup));
    }
}
