package net.vanfleteren.jazygit.state;

/**
 * Telling the user that {@code error} happened, until they close it.
 */
public record ErrorPopup(AppError error) implements Popup {
}
