package net.vanfleteren.jazygit.cli;

import io.vavr.control.Option;
import picocli.CommandLine.IVersionProvider;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * The version the jar was built as; a build that is not a jar has none.
 */
final class ManifestVersion implements IVersionProvider {

    @Override
    public String[] getVersion() {
        ResourceBundle texts = ResourceBundle.getBundle(StartupArgs.BUNDLE);
        return new String[]{Option.of(ManifestVersion.class.getPackage().getImplementationVersion())
                .map(version -> MessageFormat.format(texts.getString("jazygit.version"), version))
                .getOrElse(() -> texts.getString("jazygit.version.unknown"))};
    }
}
