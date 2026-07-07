# Chocolatey packaging

Not published to the community Chocolatey repository, that needs its own account plus
moderation review, and its scripts couldn't be built/tested with the real `choco` CLI
here (Windows-only tool). Two ways to actually use this:

## Build and install locally, no moderation needed

On Windows, with Chocolatey installed:

```
choco pack packaging\chocolatey\mcrl.nuspec
choco install mcrl -source .
```

## Submit to the community repository

Push a built `.nupkg` with `choco push` after creating an account at
[chocolatey.org](https://community.chocolatey.org) and getting an API key; see
[their docs](https://docs.chocolatey.org/en-us/create/create-packages) for the full
submission/moderation process.

To bump the version for a new mcrl release: update `<version>` in `mcrl.nuspec` and
both the URL and checksum in `tools/chocolateyinstall.ps1` (the sha256 is printed at
the end of every mcrl release, also in that release's `SHA256SUMS.txt`, uppercased for
Chocolatey's convention).
