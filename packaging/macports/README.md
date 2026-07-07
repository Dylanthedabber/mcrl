# MacPorts packaging

Not published. Unlike Homebrew, MacPorts doesn't have an informal personal-tap
workflow, every port lives in the single central
[macports-ports](https://github.com/macports/macports-ports) repository, submitted and
reviewed via PR there. This `Portfile` couldn't be tested with the real `port` tool
here either (macOS-only). To actually publish:

```
git clone https://github.com/macports/macports-ports
mkdir -p macports-ports/games/mcrl
cp packaging/macports/Portfile macports-ports/games/mcrl/
cd macports-ports/games/mcrl
port lint --nitpick Portfile
```

Fix whatever `port lint` flags, then open a PR against `macports/macports-ports`; see
their [contributing guide](https://guide.macports.org/#project.contributing) for the
full process.

To bump the version for a new mcrl release: update `version` and the `checksums` line
(the sha256 is printed at the end of every mcrl release, also in that release's
`SHA256SUMS.txt`).
