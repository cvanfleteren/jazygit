package net.vanfleteren.jazygit.state.popup;

import net.vanfleteren.jazygit.state.AppError;

/**
 * Telling the user that {@code error} happened, until they close it.
 */
public record ErrorPopup(AppError error) implements Popup {}
