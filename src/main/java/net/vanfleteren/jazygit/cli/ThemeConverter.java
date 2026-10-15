package net.vanfleteren.jazygit.cli;

import java.text.MessageFormat;
import java.util.ResourceBundle;
import net.vanfleteren.jazygit.settings.ThemeName;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Reads a theme by the name it has in the settings file.
 */
final class ThemeConverter implements ITypeConverter<ThemeName> {

    @Override
    public ThemeName convert(String value) {
        return ThemeName.of(value).getOrElseThrow(() -> new TypeConversionException(MessageFormat.format(
                ResourceBundle.getBundle(StartupArgs.BUNDLE).getString("theme.unknown"),
                value, String.join(", ", new ThemeIds()))));
    }
}
