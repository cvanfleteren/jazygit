package net.vanfleteren.jazygit.git.model.syntax;

import static net.vanfleteren.jazygit.git.model.syntax.Grammar.Delimited.comment;
import static net.vanfleteren.jazygit.git.model.syntax.Grammar.Delimited.quoted;
import static net.vanfleteren.jazygit.git.model.syntax.Grammar.Delimited.raw;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.git.model.syntax.Grammar.Delimited;
import net.vanfleteren.jazygit.git.model.syntax.Grammar.Mode;

/**
 * A language the lexer knows (ADR 53), with what it needs to scan it and how its files are told apart.
 */
public enum Language {
    JAVA(
            Grammar.code(
                    """
                    abstract assert boolean break byte case catch char class const continue default do double else
                    enum extends final finally float for goto if implements import instanceof int interface long
                    native new package permits private protected public record return sealed short static strictfp
                    super switch synchronized this throw throws transient try var void volatile when while yield true
                    false null
                    """,
                    List.of("//"),
                    List.of(comment("/*", "*/")),
                    List.of(quoted("\"\"\"").overLines(), quoted("\""), quoted("'"))),
            "",
            "java",
            ""),
    KOTLIN(
            Grammar.code(
                    """
                    abstract actual annotation as break catch class companion const constructor continue crossinline
                    do else enum expect external false final finally for fun if import in infix init inline inner
                    interface internal is lateinit noinline null object open operator override package private
                    protected public reified return sealed super suspend tailrec this throw true try typealias typeof
                    val var vararg when where while
                    """,
                    List.of("//"),
                    List.of(comment("/*", "*/")),
                    List.of(raw("\"\"\"").overLines(), quoted("\""), quoted("'"))),
            "",
            "kt kts",
            ""),
    /**
     * JavaScript and TypeScript, which has its keywords and some more.
     */
    JAVASCRIPT(
            Grammar.code(
                    """
                    abstract as async await break case catch class const continue debugger declare default delete do
                    else enum export extends false finally for function if implements import in infer instanceof
                    interface keyof let namespace new null package private protected public readonly return satisfies
                    static super switch this throw true try typeof undefined var void while with yield
                    """,
                    List.of("//"),
                    List.of(comment("/*", "*/")),
                    List.of(quoted("`").overLines(), quoted("\""), quoted("'"))),
            "",
            "js mjs cjs jsx ts mts cts tsx",
            "node deno bun"),
    PYTHON(
            Grammar.code(
                    """
                    False None True and as assert async await break case class continue def del elif else except
                    finally for from global if import in is lambda nonlocal not or pass raise return try while with
                    yield
                    """,
                    List.of("#"),
                    List.empty(),
                    prefixed(
                            List.of(quoted("\"\"\"").overLines(), quoted("'''").overLines(), quoted("\""), quoted("'")),
                            "r b f u R B F U rb rB Rb RB br bR Br BR fr fR Fr FR rf rF Rf RF")),
            "",
            "py pyi pyw",
            "python"),
    GO(
            Grammar.code(
                    """
                    break case chan const continue default defer else fallthrough for func go goto if import interface
                    map package range return select struct switch type var true false nil iota
                    """,
                    List.of("//"),
                    List.of(comment("/*", "*/")),
                    List.of(raw("`").overLines(), quoted("\""), quoted("'"))),
            "",
            "go",
            ""),
    /**
     * Rust, without its characters: their quote also starts a lifetime, which would run to the end of the line.
     */
    RUST(Grammar.code("""
                    as async await break const continue crate dyn else enum extern false fn for if impl in let loop
                    match mod move mut pub ref return self Self static struct super trait true type union unsafe use
                    where while
                    """, List.of("//"), List.of(comment("/*", "*/")), List.of(quoted("\""))), "", "rs", ""),
    /**
     * C and C++: C++ has the keywords of C and more, so a header ({@code .h}) is read as C++.
     */
    C(
            Grammar.code("""
                    alignas alignof asm auto bool break case catch char char8_t char16_t char32_t class concept const
                    consteval constexpr constinit const_cast continue co_await co_return co_yield decltype default
                    delete do double dynamic_cast else enum explicit export extern false float for friend goto if
                    inline int long mutable namespace new noexcept nullptr operator override private protected public
                    register reinterpret_cast requires restrict return short signed sizeof static static_assert
                    static_cast struct switch template this thread_local throw true try typedef typeid typename union
                    unsigned using virtual void volatile wchar_t while NULL
                    """, List.of("//"), List.of(comment("/*", "*/")), List.of(quoted("\""), quoted("'"))),
            "",
            "c h cc cpp cxx c++ hh hpp hxx h++ ino",
            ""),
    SHELL(
            Grammar.code("""
                    if then else elif fi for while until do done case esac in function select return local export
                    readonly declare typeset unset shift break continue exit source alias set trap eval exec
                    """, List.of("#"), List.empty(), List.of(quoted("\""), raw("'"))),
            ".bashrc .bash_profile .bash_login .bash_logout .profile .zshrc .zshenv .zprofile .zlogin .envrc PKGBUILD",
            "sh bash zsh ksh",
            "sh bash zsh ksh dash ash"),
    SQL(
            Grammar.code("""
                    add all alter and any as asc begin between by case cascade check column commit constraint create
                    cross database default delete desc distinct drop else end exists false foreign from full group
                    having if in index inner insert intersect into is join key left like limit not null offset on or
                    order outer primary references replace returning right rollback select set table then to true
                    truncate union unique update using values view when where with
                    """, List.of("--"), List.of(comment("/*", "*/")), List.of(raw("'")))
                    .ignoringCase(),
            "",
            "sql",
            ""),
    /**
     * JSON, and JSON with comments.
     */
    JSON(
            Grammar.code(
                    "true false null", List.of("//"), List.of(comment("/*", "*/")), List.of(quoted("\""), raw("'"))),
            "flake.lock .prettierrc .eslintrc .babelrc",
            "json jsonc json5",
            ""),
    YAML(
            Grammar.code("true false null", List.of("#"), List.empty(), List.of(quoted("\""), raw("'"))),
            ".clang-format .clang-tidy",
            "yaml yml",
            ""),
    TOML(
            Grammar.code(
                    "true false",
                    List.of("#"),
                    List.empty(),
                    List.of(quoted("\"\"\"").overLines(), raw("'''").overLines(), quoted("\""), raw("'"))),
            "Cargo.lock uv.lock poetry.lock",
            "toml",
            ""),
    /**
     * XML and HTML.
     */
    XML(
            new Grammar(
                    Mode.MARKUP,
                    HashSet.empty(),
                    false,
                    List.empty(),
                    List.of(comment("<!--", "-->")),
                    List.of(raw("\"").overLines(), raw("'").overLines())),
            "",
            "xml html htm xhtml svg xsd xsl xslt fxml iml plist csproj props targets",
            ""),
    MARKDOWN(
            new Grammar(
                    Mode.MARKDOWN, HashSet.empty(), false, List.empty(), List.of(comment("<!--", "-->")), List.empty()),
            "",
            "md markdown mdx",
            "");

    private final Grammar grammar;
    private final Set<String> names;
    private final Set<String> extensions;
    private final Set<String> interpreters;

    /**
     * @param names        the names of its files that say so as a whole, like {@code .bashrc}
     * @param extensions   the extensions of its files, in lower case
     * @param interpreters the programs a shebang names to run it; a version after the name is left out
     */
    Language(Grammar grammar, String names, String extensions, String interpreters) {
        this.grammar = grammar;
        this.names = Grammar.words(names);
        this.extensions = Grammar.words(extensions);
        this.interpreters = Grammar.words(interpreters);
    }

    Grammar grammar() {
        return grammar;
    }

    /**
     * The language of the file at {@code path}, whose first line is {@code firstLine} when that is known: by the
     * name of the file as a whole, else by its extension, else by the program its shebang runs.
     */
    public static Option<Language> of(String path, Option<String> firstLine) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        List<Language> all = List.of(values());
        return all.find(language -> language.names.contains(name))
                .orElse(() -> extension(name)
                        .flatMap(extension -> all.find(language -> language.extensions.contains(extension))))
                .orElse(() -> firstLine
                        .flatMap(Language::interpreter)
                        .flatMap(program -> all.find(language -> language.runs(program))));
    }

    private static Option<String> extension(String name) {
        int dot = name.lastIndexOf('.');
        return Option.when(
                dot > 0 && dot < name.length() - 1,
                () -> name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * The program the shebang {@code line} runs, if it is one: the last part of its path, or the program after
     * {@code env}.
     */
    private static Option<String> interpreter(String line) {
        if (!line.startsWith("#!")) {
            return Option.none();
        }
        List<String> words = List.of(line.substring(2).strip().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(word.lastIndexOf('/') + 1));
        return words.headOption()
                .flatMap(program -> program.equals("env")
                        ? words.tail().find(word -> !word.startsWith("-") && !word.contains("="))
                        : Option.of(program));
    }

    /**
     * Whether a shebang that names {@code program} runs this language: {@code python3.12} runs Python.
     */
    private boolean runs(String program) {
        return interpreters.exists(interpreter -> program.startsWith(interpreter)
                && program.substring(interpreter.length()).chars().allMatch(c -> Character.isDigit(c) || c == '.'));
    }

    /**
     * The {@code strings}, and each of them after each of the {@code prefixes}, like Python's {@code f"…"}.
     */
    private static List<Delimited> prefixed(List<Delimited> strings, String prefixes) {
        return strings.appendAll(List.ofAll(Grammar.words(prefixes))
                .flatMap(prefix -> strings.map(string ->
                        new Delimited(prefix + string.open(), string.close(), string.multiline(), string.escapes()))));
    }
}
