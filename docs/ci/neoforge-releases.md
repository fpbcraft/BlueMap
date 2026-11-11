# NeoForge 1.21.1 builds and releases

The fork's deployment branch is `perf/v5.7-lazy-textures`. Its default CI builds/tests core, common and NeoForge only. `-PbluemapPlatforms=neoforge` excludes other platform projects from the settings graph; explicit task paths and configuration-on-demand avoid configuring unrelated builds. Without the property, local/upstream builds still include all platforms. The NeoForge target is now explicitly Minecraft 1.21.1 / NeoForge 21.1.248, matching Player History, rather than compiling against the old 1.21 beta while advertising several versions.

## Release controls

After this workflow is merged into the default/deployment branch, open Actions → Semantic Release → Run workflow. Select patch/minor/major or supply an exact three-component SemVer. This mirrors Player History's manual semantic-version/tag workflow; it is not npm semantic-release's automatic commit classification.

Successful releases publish the verified NeoForge jar to GitHub Releases and retain an Actions artifact. `RELEASE_VERSION` is validated and used consistently in jar filenames and metadata. Existing release versions are immutable. Manual releases must run from `perf/v5.7-lazy-textures`; pushed release tags must point to a commit reachable from that branch. Prerelease versions are marked prerelease. The workflow creates no tag/release until the build and tests succeed.

The version helper interprets legacy reachable tags such as `v5.7` as baseline 5.7.0, so the first patch defaults to 5.7.1. Unrelated newer upstream master tags do not advance the production version. Exact versions still require complete SemVer. Branch development artifacts use `<reachable-version>-dev.<sha>`.

The optional “include CLI” release input also builds/uploads the standalone CLI for retained texture migration. It is off by default and is not a server mod. Docker is separately opt-in under the Build workflow; normal CI and releases do not build/push a container. Existing Docker source support remains available and builds only its CLI dependency.

No release/tag is created as part of preparing this PR. Changing the repository default to the deployment branch makes the Run workflow button available once this workflow lands there; GitHub requires workflow_dispatch files on the default branch.

## Why the old Actions build was slow

Run 43033306299 spent 8m57s in `./gradlew spotlessCheck test release`, then 1m02s in the dependent Docker job. The unqualified Gradle selectors ran across CLI, Fabric, Forge, NeoForge, Paper, Spigot and Sponge, followed by a multi-architecture CLI container build. There was no Minecraft-version build matrix: the version list in the NeoForge implementation was compatibility metadata.

The new path builds one platform, skips Docker by default, caches Gradle dependencies/transforms and npm downloads, avoids duplicate push+PR feature-branch runs, and cancels superseded CI. Minecraft mapping/decompilation/recompilation still costs time on a genuinely cold NeoForge cache; narrowing platforms cannot eliminate that dependency setup.

Local validation of the full targeted command passed in 1m18s, including a newly generated 1.21.1 NeoForge artifact set whose setup took about 52 seconds. This is not a prediction of hosted Actions timing. The PR's actual CI run is the hosted comparison; cache state and hardware differ.

## Validation

- Version-tool tests cover valid/invalid tags, semantic bumps, development identity and legacy two-component BlueMap baselines.
- Targeted core/common checks and NeoForge 1.21.1 packaging pass with an explicit development release version.
- The platform graph excludes Fabric/Forge/Paper/Spigot/Sponge in the target build. Existing source projects remain available via the default/all-platform configuration.
- The published filename and embedded NeoForge metadata must match the resolved release version; inspect the CI artifact before the first manual release.

References: [Gradle configuration on demand](https://docs.gradle.org/current/userguide/configuration_on_demand.html), [GitHub manual workflow requirements](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
