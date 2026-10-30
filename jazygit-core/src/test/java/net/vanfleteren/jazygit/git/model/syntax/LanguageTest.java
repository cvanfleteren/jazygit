package net.vanfleteren.jazygit.git.model.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanguageTest {

    @ParameterizedTest
    @CsvSource({
        "src/Main.java, JAVA",
        "build.gradle.kts, KOTLIN",
        "web/app.tsx, JAVASCRIPT",
        "lib/index.MJS, JAVASCRIPT",
        "tool.py, PYTHON",
        "main.go, GO",
        "src/lib.rs, RUST",
        "include/list.h, C",
        "src/list.cpp, C",
        "run.sh, SHELL",
        "schema.sql, SQL",
        "package.json, JSON",
        ".github/workflows/ci.yml, YAML",
        "Cargo.toml, TOML",
        "pom.xml, XML",
        "site/index.html, XML",
        "README.md, MARKDOWN",
    })
    void knowsALanguageByItsExtension(String path, Language language) {
        assertEquals(Option.of(language), Language.of(path, Option.none()));
    }

    @ParameterizedTest
    @CsvSource({"home/.bashrc, SHELL", ".envrc, SHELL", "flake.lock, JSON", "Cargo.lock, TOML"})
    void knowsALanguageByTheNameOfAFileAsAWhole(String path, Language language) {
        assertEquals(Option.of(language), Language.of(path, Option.none()));
    }

    @ParameterizedTest
    @CsvSource({
        "#!/bin/sh, SHELL",
        "#!/usr/bin/env bash, SHELL",
        "#!/usr/bin/env python3, PYTHON",
        "#!/usr/bin/python3.12 -u, PYTHON",
        "#!/usr/bin/env -S node --no-warnings, JAVASCRIPT",
    })
    void knowsALanguageByItsShebang(String shebang, Language language) {
        assertEquals(Option.of(language), Language.of("bin/tool", Option.of(shebang)));
    }

    @ParameterizedTest
    @CsvSource({"bin/tool, #!/usr/bin/env ruby", "Makefile, all: build", "notes.txt, plain", ".gitignore, target/"})
    void knowsNoLanguageOfAnotherFile(String path, String firstLine) {
        assertEquals(Option.none(), Language.of(path, Option.of(firstLine)));
    }

    @ParameterizedTest
    @CsvSource({"script.py, #!/bin/sh, PYTHON", ".bashrc.json, #!/bin/sh, JSON"})
    void theNameWinsOverTheShebang(String path, String shebang, Language language) {
        assertEquals(Option.of(language), Language.of(path, Option.of(shebang)));
    }
}
