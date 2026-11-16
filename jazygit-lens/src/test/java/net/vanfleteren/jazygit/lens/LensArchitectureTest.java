package net.vanfleteren.jazygit.lens;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of jazygit-lens: those of the whole application. That the lens knows nothing of the application, the
 * compiler sees to.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.lens", importOptions = ImportOption.DoNotIncludeTests.class)
class LensArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);
}
