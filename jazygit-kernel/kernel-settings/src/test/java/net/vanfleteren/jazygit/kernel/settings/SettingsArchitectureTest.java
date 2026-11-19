package net.vanfleteren.jazygit.kernel.settings;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of kernel-settings: those of the whole application. That the settings know nothing of the
 * application, the compiler sees to.
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit.kernel.settings",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SettingsArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);
}
