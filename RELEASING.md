# Releasing to Maven Central

What a release of this repository takes. Nothing in it is a secret: the key and the token stay on your machine.

## Once

1. **Central Portal account and namespace.** Sign in at https://central.sonatype.com and add the namespace `com.alkadev`. The Portal shows a verification key: add it as a TXT record in the DNS of `alkadev.com` and click Verify.
2. **A GPG key.** Create one (`gpg --full-generate-key`), publish the public part (`gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`), and keep the private part and its passphrase to yourself.
3. **A user token** (Portal, Account, Generate User Token). Put it in `~/.m2/settings.xml`, outside every project:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>THE-TOKEN-USERNAME</username>
      <password>THE-TOKEN-PASSWORD</password>
    </server>
  </servers>
</settings>
```

### Windows: signing says `No pinentry`

Gpg4win keeps its passphrase window in `C:\Program Files\Gpg4win\bin\pinentry.exe`, where the GPG agent does not look. Tell it, in the file `%APPDATA%\gnupg\gpg-agent.conf` (one line), and restart the agent with `gpgconf --kill gpg-agent`:

```
pinentry-program C:\Program Files\Gpg4win\bin\pinentry.exe
```

The `gpg` of Git for Windows is not the one with your key: give Maven the one of Gpg4win, `-Dgpg.executable="C:\Program Files\GnuPG\bin\gpg.exe"`. The window for the passphrase opens on the first signature and the agent remembers it for a while, so it is asked once per release.

## Every release

1. Set the version everywhere: `powershell -File set-version.ps1 -Version 0.2.0`
2. Build without signing and without uploading, to see that the sources jars and the javadoc jars are made: `mvn -Prelease -Dgpg.skip=true clean verify`
3. Upload for validation: `mvn -Prelease "-Dgpg.executable=C:\Program Files\GnuPG\bin\gpg.exe" clean deploy` (on Windows; elsewhere `mvn -Prelease clean deploy`). All modules go up as one bundle, signed with your key. The bundle is validated by the Portal and then **waits**: it is not published until you click Publish in the Portal. A version that is published can never be removed or overwritten, so look at the validation result first. A bundle that you do not want can be dropped in the Portal.
4. Commit the new version and tag it (`git tag v0.2.0`).

The private repository (alka-engine) builds on the published version through the property `alka.api.version` in its root pom: change it there too.

## Releasing with a tag (GitHub Actions)

Both repositories have workflows in `.github/workflows`: `build.yml` builds on every push (and, in alka-engine, runs all the tests), `release.yml` runs when a tag `v*` is pushed.

### Once: a signing key for the workflow, and the secrets

A key of its own for the workflow keeps your main key on your own machine. In a terminal (the `gpg` of Gpg4win; it asks for a passphrase in a window):

```
gpg --quick-generate-key "Alka release signing (Lejla Omerovic)" rsa4096 sign never
gpg --list-secret-keys --keyid-format long
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <KEYID>
gpg --armor --export-secret-keys <KEYID>
```

The last command prints the private key as text: copy all of it, from `-----BEGIN PGP PRIVATE KEY BLOCK-----` to the end. Then, on GitHub, in the repository, **Settings, Secrets and variables, Actions, New repository secret**:

| Repository | Secret | Value |
|---|---|---|
| alka | `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` | the user token of the Central Portal |
| alka | `GPG_PRIVATE_KEY` | the text of the private key |
| alka | `GPG_PASSPHRASE` | its passphrase |
| alka-engine | `PUBLIC_REPO_TOKEN` | a fine-grained access token (below) |

The token: GitHub, your avatar, **Settings, Developer settings, Personal access tokens, Fine-grained tokens, Generate new token**. Resource owner: you; repository access: **Only select repositories**, `LejlaO/alka`; permission **Contents: Read and write**. It is only used to attach the package to the release page of the public repository.

### Every release

1. `powershell -File set-version.ps1 -Version 0.2.0` in this repository, and the property `alka.api.version` and the version in the poms of alka-engine; commit and push both.
2. Tag **this** repository first, then alka-engine, with the same name: `git tag v0.2.0` and `git push origin v0.2.0`. The workflow here builds, signs and uploads the bundle (validated, waiting) and makes the release page. The workflow of alka-engine builds the package against this tag, runs all the tests and attaches the zips and `SHA256SUMS.txt` to that page.
3. In the Central Portal, look at the validated deployment and click Publish when you are sure.

To try it without publishing, use a version like `0.1.1-test` (`set-version.ps1` accepts it), tag, look at the result, then **Drop** the deployment in the Portal and delete the tag and the release page.

## Notes for plugin authors

`alka-plugin-parent` carries the license, the developer and the source link of Alka. A plugin that is published to Central needs its own: put `<licenses>`, `<developers>` and `<scm>` in the pom of the plugin (they replace the inherited ones).
