package net.vanfleteren.jazygit.core.askpass;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.CoreRules;

/**
 * The rules of core-askpass: those of the whole core. That it knows nothing of the app, its state or TamboUI the
 * compiler sees to.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.core.askpass", importOptions = ImportOption.DoNotIncludeTests.class)
class AskpassArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);
}
