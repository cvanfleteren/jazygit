package net.vanfleteren.jazygit.process;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.CoreRules;

/**
 * The rules of jazygit-process: those of the whole core.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.process", importOptions = ImportOption.DoNotIncludeTests.class)
class ProcessArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);
}
