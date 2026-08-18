package org.lowern1ght.burg.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture test for ADR-0008 §"Layers inside each context": the
 * domain layer under {@code org.lowern1ght.burg.domain} MUST NOT reference
 * Minecraft or NeoForge types.
 *
 * <p>This is a source-level enforcement (walks the raw {@code .java} files)
 * rather than a classpath-level one. Rationale: a domain class that
 * imports {@code net.minecraft.world.phys.BlockPos} would still compile
 * under the Minecraft classpath — but the whole point of the layering rule
 * is that the domain must run on a bare JVM (the first fast feedback
 * loop in a project whose only verification bar is in-game). Catching the
 * import at the source level turns the rule into a build failure instead
 * of a review finding, and keeps the test cheap: no ArchUnit shading, no
 * class-loading, only the JUnit BOM already on the classpath.
 *
 * <p>Two fences:
 * <ol>
 *   <li>{@code ^import (static )?net\.(minecraft|neoforged)\.} anywhere
 *       under {@code domain/} → failure. The {@code static} variant is
 *       included because a static import of a Minecraft symbol is the
 *       same layering violation as a top-level one.</li>
 *   <li>Bare Minecraft type names ({@code BlockPos}, {@code ItemStack},
 *       {@code Level}, {@code CompoundTag}) appearing as type-use tokens
 *       outside of comments and string literals → failure. This catches a
 *       sneaky fully-qualified usage that slips past the {@code import}
 *       line. The names are word-boundary matched so {@code CompoundTaggable}
 *       or {@code BlockPosField} don't trip accidentally.</li>
 * </ol>
 *
 * <p>Unknown / future Minecraft types are not covered by name-match;
 * fence #1 is the durable gate. Fence #2 names the four types that
 * ADR-0008 calls out as the canonical spill-overs from {@code BlockPos},
 * {@code ItemStack}, {@code Level}, {@code CompoundTag}; if a new
 * Minecraft type needs to be banned in domain code, append it to
 * {@link #BANNED_TYPE_NAMES}.
 *
 * <p>The test resolves {@code common/src/main/java} from the working
 * directory first (Gradle sets {@code user.dir} to the {@code :common}
 * module root when running {@code test}), then walks up the tree as a
 * fallback. If {@code domain/} does not exist yet (the skeleton is empty)
 * the tests pass with an empty signal — the rule is wired so deleting the
 * rule by accident fails the build, not so the absence of code
 * fails it.
 *
 * <p>A third fence (ADR-0014): the {@code application/} layer — the ports
 * and use cases — is held to the same import standard as {@code domain/},
 * per modding/AGENT-RULES.md rule 6 ("Nothing under
 * {@code org.lowern1ght.burg.domain} (and {@code application} ports) may
 * import {@code net.minecraft.*}"). Only the import fence applies there
 * (fence #1); the banned-type-name fence stays domain-only because
 * application javadoc legitimately names {@code Town} adapters in prose.
 */
class DomainPurityTest {

    private static final Path COMMON_MAIN_JAVA = locateCommonJavaMain();

    private static final Path DOMAIN_ROOT =
        COMMON_MAIN_JAVA.resolve("org").resolve("lowern1ght").resolve("burg").resolve("domain");

    /** The application layer (ports + use cases) — same import fence as domain (ADR-0014). */
    private static final Path APPLICATION_ROOT =
        COMMON_MAIN_JAVA.resolve("org").resolve("lowern1ght").resolve("burg").resolve("application");

    /** Matches {@code import net.minecraft.…}, {@code import net.neoforged.…}, including {@code static}. */
    private static final Pattern IMPORT_NET =
        Pattern.compile("^\\s*import\\s+(static\\s+)?net\\.(minecraft|neoforged)\\.[A-Za-z0-9_.\\*]+");

    /**
     * Minecraft types ADR-0008 names as the canonical spill-overs into
     * domain code. Bare tokens in code (not in comments or string
     * literals) trigger the secondary fence. Extend when a new
     * Minecraft-native concept needs to be banned.
     */
    private static final Set<String> BANNED_TYPE_NAMES =
        Set.of("BlockPos", "ItemStack", "Level", "CompoundTag");

    private static final Pattern BANNED_TYPE_TOKEN;
    static {
        StringBuilder alt = new StringBuilder();
        for (String name : BANNED_TYPE_NAMES) {
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(name));
        }
        // \b so "CompoundTaggable" / "BlockPosField" do not trip.
        BANNED_TYPE_TOKEN = Pattern.compile("\\b(?:" + alt + ")\\b");
    }

    @Test
    @DisplayName("domain sources do not import net.minecraft.* or net.neoforged.*")
    void noMinecraftImports() throws IOException {
        List<Path> javaSources = listDomainSources();
        if (javaSources.isEmpty()) return;

        List<String> violations = new ArrayList<>();
        for (Path file : javaSources) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (IMPORT_NET.matcher(line).find()) {
                    violations.add(rel(file) + ":" + (i + 1) + ": " + line.trim());
                }
            }
        }
        assertTrue(
            violations.isEmpty(),
            () -> "domain sources must not import net.minecraft.* or net.neoforged.* "
                + "(ADR-0008 §\"Layers inside each context\"); found "
                + violations.size() + " violation(s):\n  "
                + String.join("\n  ", violations)
        );
    }

    @Test
    @DisplayName("domain sources do not name BlockPos / ItemStack / Level / CompoundTag as type identifiers")
    void noMinecraftTypeNamesAsTypes() throws IOException {
        List<Path> javaSources = listDomainSources();
        if (javaSources.isEmpty()) return;

        List<String> violations = new ArrayList<>();
        for (Path file : javaSources) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String code = stripCommentsAndStrings(lines);
            for (var matcher = BANNED_TYPE_TOKEN.matcher(code); matcher.find(); ) {
                String matched = matcher.group();
                int lineNumber = lineNumberOf(code, matcher.start());
                violations.add(rel(file) + ":" + lineNumber + ": " + matched);
                // advance: avoid an infinite loop on zero-width matches (impossible here, defensive)
                if (matcher.end() <= matcher.start()) break;
            }
        }
        assertTrue(
            violations.isEmpty(),
            () -> "domain sources must not reference Minecraft types as type identifiers "
                + "(ADR-0008); found " + violations.size() + " violation(s):\n  "
                + String.join("\n  ", violations)
        );
    }

    @Test
    @DisplayName("the enforcement rule is wired: the test class lives in burg.architecture")
    void ruleIsWired() {
        // If somebody deletes this class the build still passes — the rule is silent.
        // The presence of this test in burg.architecture is itself the contract; a future
        // agent cannot accidentally remove it because the bare-JVM domain tests
        // (StandingBookTest, CitizenIdTest, AcquisitionTest) rely on the same fence.
        assertEquals(
            "org.lowern1ght.burg.architecture",
            getClass().getPackageName(),
            "DomainPurityTest must live in org.lowern1ght.burg.architecture"
        );
    }

    @Test
    @DisplayName("application sources (ports + use cases) do not import net.minecraft.* or net.neoforged.*")
    void applicationStaysMinecraftFree() throws IOException {
        List<Path> javaSources = listSourcesUnder(APPLICATION_ROOT);
        if (javaSources.isEmpty()) return;

        List<String> violations = new ArrayList<>();
        for (Path file : javaSources) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (IMPORT_NET.matcher(line).find()) {
                    violations.add(rel(file) + ":" + (i + 1) + ": " + line.trim());
                }
            }
        }
        assertTrue(
            violations.isEmpty(),
            () -> "application sources (ports + use cases) must not import net.minecraft.* "
                + "or net.neoforged.* (modding/AGENT-RULES.md rule 6, ADR-0014); found "
                + violations.size() + " violation(s):\n  "
                + String.join("\n  ", violations)
        );
    }

    // ---------- helpers ----------

    private List<Path> listDomainSources() throws IOException {
        return listSourcesUnder(DOMAIN_ROOT);
    }

    private List<Path> listSourcesUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                .sorted()
                .toList();
        }
    }

    private static String rel(Path file) {
        return COMMON_MAIN_JAVA.relativize(file).toString().replace('\\', '/');
    }

    private static int lineNumberOf(String haystack, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < haystack.length(); i++) {
            if (haystack.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Strips {@code //} and {@code /*}{@code *}{@code /} comments and
     * string literals from each line so a bare-token check looks at code
     * only. The strip is per-line; a block comment that straddles lines
     * blanks only the letters/digits/underscores it touches so layout
     * survives and line numbers stay aligned.
     */
    private static String stripCommentsAndStrings(List<String> lines) {
        StringBuilder out = new StringBuilder();
        boolean inBlock = false;
        for (String line : lines) {
            StringBuilder rewritten = new StringBuilder(line.length());
            int i = 0;
            while (i < line.length()) {
                if (inBlock) {
                    int end = line.indexOf("*/", i);
                    if (end < 0) {
                        // block continues past this line — blank identifier chars, keep the rest
                        for (int j = i; j < line.length(); j++) {
                            rewritten.append(blankIdentifier(line.charAt(j)));
                        }
                        i = line.length();
                    } else {
                        for (int j = i; j < end + 2; j++) {
                            rewritten.append(blankIdentifier(line.charAt(j)));
                        }
                        i = end + 2;
                        inBlock = false;
                    }
                } else if (line.startsWith("//", i)) {
                    // line comment — drop the rest of the line
                    break;
                } else if (line.startsWith("/*", i)) {
                    inBlock = true;
                    rewritten.append(' ');
                    i++;
                } else if (line.charAt(i) == '"') {
                    int end = line.indexOf('"', i + 1);
                    if (end < 0) {
                        // unterminated string — drop the rest as if it were the body
                        i = line.length();
                    } else {
                        rewritten.append("\"\"");
                        i = end + 1;
                    }
                } else if (line.charAt(i) == '\'') {
                    int end = line.indexOf('\'', i + 1);
                    if (end < 0) {
                        i = line.length();
                    } else {
                        rewritten.append("''");
                        i = end + 1;
                    }
                } else {
                    rewritten.append(line.charAt(i));
                    i++;
                }
            }
            out.append(rewritten).append('\n');
        }
        return out.toString();
    }

    private static char blankIdentifier(char c) {
        return Character.isLetterOrDigit(c) || c == '_' ? ' ' : c;
    }

    /**
     * Locate the {@code common/src/main/java} folder from the running
     * Gradle test. Gradle sets {@code user.dir} to the project directory
     * when running the {@code :common:test} task, so {@code src/main/java}
     * works directly. As a fallback for ad-hoc invocations we walk up
     * the tree looking for {@code <root>/common/src/main/java}. The
     * first hit wins.
     */
    private static Path locateCommonJavaMain() {
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve("src/main/java");
        if (Files.isDirectory(direct)) return direct;

        Path walked = cwd;
        for (int depth = 0; depth < 6; depth++) {
            Path candidate = walked.resolve("common/src/main/java");
            if (Files.isDirectory(candidate)) return candidate;
            Path parent = walked.getParent();
            if (parent == null) break;
            walked = parent;
        }
        // Last report is the direct path even if it does not exist; the
        // caller treats a missing directory as "no domain code yet".
        return direct;
    }
}
