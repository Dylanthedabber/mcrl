# .deb packaging

Builds a standalone, installable `.deb` (verified: built and inspected here with
`ar`/`tar`, correct structure, jar hash matches the real release). This is a one-shot
package, not an APT repository, so `apt upgrade` won't pick up new mcrl versions on its
own; a real repo (signed `Release`/`Packages` files hosted somewhere, e.g. via
`aptly` or GitHub Pages) is a bigger separate undertaking not done here.

```
./build.sh
sudo apt install ./mcrl_1.3.0_all.deb
```

Installs `mcrl.jar` to `/usr/share/mcrl/mcrl.jar` and prints a reminder to point
`JDK_JAVA_OPTIONS` at it (or just run the full installer instead, see the main README).

To bump the version for a new mcrl release: update `VERSION` in `build.sh` and
`Version:` in `control`, then rerun `./build.sh`.
