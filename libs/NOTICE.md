# Third-party libraries in this folder

The build compiles against three jars from **Shizuku** (https://shizuku.rikka.app/,
https://github.com/RikkaApps/Shizuku), used to run privileged shell commands without root:

| file | version | purpose |
|---|---|---|
| `shizuku-api.jar` | 13.0.0 | `Shizuku.newProcess` - runs a command with the shell UID |
| `shizuku-shared.jar` | 13.0.0 | shared classes required by the API |
| `shizuku-provider.jar` | 13.0.0 | the `ShizukuProvider` declared in AndroidManifest.xml |

Shizuku is licensed under the **Apache License 2.0**; the jars are redistributed here unmodified.

Version note: 13.0.0 is pinned on purpose - `Shizuku.newProcess` was made private in later
releases, so anything newer does not compile without a rewrite.

`shizuku-provider-manifest.xml` and `shizuku-provider-res.zip` are unused leftovers and are not
part of the build (they are harmless; the provider is declared directly in `AndroidManifest.xml`).
