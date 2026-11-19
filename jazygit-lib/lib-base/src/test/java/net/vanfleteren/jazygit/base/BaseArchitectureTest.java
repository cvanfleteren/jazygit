package net.vanfleteren.jazygit.base;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of lib-base: those of the whole application. That the base knows nothing of the application, the
 * compiler sees to.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.base", importOptions = ImportOption.DoNotIncludeTests.class)
class BaseArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);
}
