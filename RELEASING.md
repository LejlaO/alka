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

## Notes for plugin authors

`alka-plugin-parent` carries the license, the developer and the source link of Alka. A plugin that is published to Central needs its own: put `<licenses>`, `<developers>` and `<scm>` in the pom of the plugin (they replace the inherited ones).
