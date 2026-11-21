package net.vanfleteren.jazygit.core.forgejo;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.CoreRules;

/**
 * The rules of core-forgejo: those of the whole core.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.core.forgejo", importOptions = ImportOption.DoNotIncludeTests.class)
class ForgejoArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);
}
