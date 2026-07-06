# Mcrl (pronounced "em-curl") - Minecraft Chat Restrictions Lifted

A JVM agent that clears the Microsoft/Xbox account-level "chat disabled" gate on the
Minecraft Java client. It does not touch chat signing/reporting - see the "What this
does and doesn't do" section below.

It's a `-javaagent`, not a mod, so it attaches below whatever mod loader (or nothing)
is running, and matches its target by shape rather than by class/method name - so the
same jar works across loaders without a rebuild per loader. Two real shapes exist
depending on the game's era, and this agent handles both:

- **1.19 - 1.21.11** (Forge/NeoForge: `Minecraft.getChatStatus()`, Fabric/Quilt:
  `MinecraftClient.getChatRestriction()`): a single enum with constants `ENABLED`,
  `DISABLED_BY_OPTIONS`, `DISABLED_BY_PROFILE`, `DISABLED_BY_LAUNCHER`, matched by that
  constant set regardless of class/method name.
- **26.1+**: Mojang restructured this into a `ChatAbilities` object built from a set of
  `ChatRestriction` reasons (no more `ENABLED` constant - "no restriction" is just an
  empty set). Matched by the fluent `addRestriction(ChatRestriction) -> same type`
  method shape instead.

Mojang also removed all obfuscation from the Java client starting with 26.1, so for
that era and later this agent works on **unmodified vanilla too**, not just under a mod
loader - there's no deobfuscation step left to depend on. For 1.19 - 1.21.11, a mod
loader (Forge/NeoForge/Fabric/Quilt) is still required, since true vanilla ships those
versions obfuscated and the readable enum names this agent looks for don't exist in
that bytecode at all.

**Verified**, not assumed: every one of the 24 release versions from 1.19 through
1.21.11, plus 26.1 and 26.2, was checked against real client bytecode (old versions
remapped using Mojang's own published mappings to reconstruct what Forge/Fabric expose;
26.x checked directly since it ships unobfuscated) and confirmed to match the shape
this agent looks for.

## Install (Windows)

1. Put this whole `Mcrl` folder inside your **Documents** folder, so the jar ends up at:
   `Documents\Mcrl\mcrl.jar`
2. Press `Win + R`, paste this, press Enter:

   ```
   cmd /c setx JDK_JAVA_OPTIONS "-javaagent:%USERPROFILE%\Documents\Mcrl\mcrl.jar"
   ```

3. Close every Minecraft launcher window that's currently open (official launcher,
   PrismLauncher, CurseForge, whatever), then reopen and launch normally.

That's it. This applies automatically from now on - no per-instance JVM argument,
no re-running this after launcher/game updates. Works for any Minecraft Java version
and any loader (Forge, NeoForge, Fabric, Quilt, vanilla).

If your Windows account's Documents folder is redirected by OneDrive (some setups
move it to `...\OneDrive\Documents`), point the path in step 2 at wherever the folder
actually landed instead.

## Linux / macOS

Same idea, no launcher-sandbox issues on macOS. On Linux, if your launcher is a
Flatpak (e.g. PrismLauncher), a plain shell environment variable won't reach it - use:

```
flatpak override --user --env=JDK_JAVA_OPTIONS='-javaagent:/path/to/Mcrl/mcrl.jar' org.prismlauncher.PrismLauncher
```

For a native (non-Flatpak) install, add to your shell profile (`~/.bashrc`, `~/.zshrc`,
or macOS equivalent):

```
export JDK_JAVA_OPTIONS="-javaagent:/path/to/Mcrl/mcrl.jar"
```

## What this does and doesn't do

- **Does:** overrides the client-side `DISABLED_BY_PROFILE` chat gate (the check tied
  to the Microsoft/Xbox account's "Online Safety" / communication privacy setting) so
  the game always allows chat, regardless of that account flag. Every other
  restriction reason (`DISABLED_BY_OPTIONS`, `DISABLED_BY_LAUNCHER`, and their 26.x
  equivalents) is left untouched.
- **Doesn't:** touch chat message signing or the report-to-Mojang pipeline introduced
  in 1.19. That's a separate, much larger system (packet structure, not a simple enum
  gate) handled by unrelated tools like No Chat Reports / FreedomChat.
- **Side effect:** `JDK_JAVA_OPTIONS` applies to *every* Java program you run
  afterward, not just Minecraft. Harmless functionally (the agent no-ops on anything
  that isn't the game), but you'll see a one-line notice print to the console of any
  unrelated Java program you run.
- **Requires Java 17+ to run the agent itself**, same as Minecraft's own minimum for
  1.19+.

## Version coverage (verified against real bytecode, not assumed)

| Range | Shape matched | Works on vanilla? | Works under Forge/NeoForge/Fabric/Quilt? |
|---|---|---|---|
| 1.19 - 1.21.11 (24 releases) | legacy enum getter | No (ships obfuscated; readable names don't exist in the raw jar) | Yes |
| 26.1, 26.2 | modern `ChatAbilities` builder | Yes (ships unobfuscated) | Yes |

If a future version restructures the feature again in a way that matches neither
shape, the agent simply never finds anything to patch - it prints its install banner
but no `found ... enum` / `patching ...` lines, which is how you'd notice it isn't
doing anything on that version.

## Building from source

Requires JDK 17+ and network access for Gradle to fetch dependencies (ASM) and the
Shadow plugin.

```
./gradlew shadowJar   # or: gradle shadowJar
```

Output: `build/libs/mcrl.jar`.
