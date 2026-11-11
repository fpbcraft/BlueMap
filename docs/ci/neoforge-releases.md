# NeoForge 1.21.1 builds and releases

The fork's deployment branch is `v5.7-fpb`. Its default CI builds/tests core, common and NeoForge only. `-PbluemapPlatforms=neoforge` excludes other platform projects from the settings graph; explicit task paths and configuration-on-demand avoid configuring unrelated builds. Without the property, local/upstream builds still include all platforms. The NeoForge target is now explicitly Minecraft 1.21.1 / NeoForge 21.1.248, matching Player History, rather than compiling against the old 1.21 beta while advertising several versions.

## Release controls

Open Actions → Release BlueMap 5.7 → Run workflow. Leaving the version empty increments only the patch number: 5.7.1, 5.7.2, and so on. An exact version must stay on 5.7.x. Major/minor bumps are intentionally unavailable because addons such as bluemapmodelloaders require BlueMap >=5.7 and <5.8.

Successful releases publish the verified NeoForge jar and an Actions artifact. Both the version helper and Gradle reject incompatible RELEASE_VERSION values. The workflow inspects the actual mod metadata inside the finished jar before upload. Manual releases run from v5.7-fpb; pushed v5.7.* tags must be reachable from that branch. Versions are immutable.

Only reachable 5.7.x tags count when choosing versions. Legacy v5.7 means 5.7.0; the accidental v6.0.0 release and newer upstream lines are ignored. Development builds use the next compatible patch plus -dev.<sha>. Stable releases are explicitly marked Latest so the higher-numbered accidental release does not remain the default download. Existing v6.0.0 assets/tags are not overwritten or deleted by this change.

The optional “include CLI” release input also builds/uploads the standalone CLI for retained texture migration. It is off by default and is not a server mod. Docker is separately opt-in under the Build workflow; normal CI and releases do not build/push a container. Existing Docker source support remains available and builds only its CLI dependency.

No release/tag is created as part of preparing this PR. Changing the repository default to the deployment branch makes the Run workflow button available once this workflow lands there; GitHub requires workflow_dispatch files on the default branch.

## Why the old Actions build was slow

Run 43033306299 spent 8m57s in `./gradlew spotlessCheck test release`, then 1m02s in the dependent Docker job. The unqualified Gradle selectors ran across CLI, Fabric, Forge, NeoForge, Paper, Spigot and Sponge, followed by a multi-architecture CLI container build. There was no Minecraft-version build matrix: the version list in the NeoForge implementation was compatibility metadata.

The new path builds one platform, skips Docker by default, caches Gradle dependencies/transforms and npm downloads, avoids duplicate push+PR feature-branch runs, and cancels superseded CI. Minecraft mapping/decompilation/recompilation still costs time on a genuinely cold NeoForge cache; narrowing platforms cannot eliminate that dependency setup.

Local validation of the full targeted command passed in 1m18s, including a newly generated 1.21.1 NeoForge artifact set whose setup took about 52 seconds. This is not a prediction of hosted Actions timing. The PR's actual CI run is the hosted comparison; cache state and hardware differ.

## Validation

- Version-tool tests cover ignoring v6.0.0, sequential 5.7 patches, incompatible exact versions, development identity and legacy two-component baselines. Metadata tests reject mislabeled jars even when their filenames look compatible.
- Targeted core/common checks and NeoForge 1.21.1 packaging pass with an explicit development release version.
- The platform graph excludes Fabric/Forge/Paper/Spigot/Sponge in the target build. Existing source projects remain available via the default/all-platform configuration.
- The published filename and embedded NeoForge metadata must match the resolved release version; inspect the CI artifact before the first manual release.

References: [Gradle configuration on demand](https://docs.gradle.org/current/userguide/configuration_on_demand.html), [GitHub manual workflow requirements](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).
