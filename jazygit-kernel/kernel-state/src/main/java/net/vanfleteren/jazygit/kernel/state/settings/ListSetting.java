package net.vanfleteren.jazygit.kernel.state.settings;

import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;

/**
 * A list of the settings that the settings panel edits, as the agents (ADR 164): the panel shows it in a row of its
 * own, which opens a dialog of its entries, where they are added, edited, deleted and moved; a form edits one. Whoever
 * owns the list declares it as a constant.
 *
 * @param key      the setting that holds the list
 * @param titleKey the message key of the title of the dialog of its entries
 * @param name     how an entry is named in that dialog, and in the panel's row
 * @param form     how an entry is edited
 * @param <E>      what an entry is
 */
public record ListSetting<E>(SettingKey<Entries<E>> key, String titleKey, Function<E, String> name, Form<E> form) {}
