# Sets the version of every module of this repository in one go, for a release:   powershell -File set-version.ps1 -Version 0.2.0
# It changes the version of all poms and the references to the parents, the property alka.version (the parent poms use it for the API and for the Maven plugin),
# and the three places that hold the version as text: the pom and the README of the archetype, and the README of the repository.
# Afterwards: mvn -Prelease clean verify (without signing: -Dgpg.skip=true), then deploy. The private repository (alka-engine) names the version it builds on in
# the property alka.api.version of its root pom: change it there too.
param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^\d+\.\d+\.\d+(-[A-Za-z0-9.]+)?$') { throw "The version '$Version' is not like 0.2.0 or 0.2.0-rc1." }
$command = Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1
$mvn = if ($command) { $command.Source } else { (Get-ChildItem (Join-Path $HOME '.m2\wrapper\dists\*\*\*\bin\mvn.cmd') -ErrorAction SilentlyContinue | Select-Object -First 1).FullName }
if (-not $mvn) { throw 'Maven was not found (not on the PATH, and no wrapper distribution in ~\.m2).' }
Set-Location $PSScriptRoot
$versions = 'org.codehaus.mojo:versions-maven-plugin:2.23.0'

function Maven($arguments) {
    & $mvn -B -q @arguments
    if ($LASTEXITCODE -ne 0) { throw "Maven failed: $($arguments -join ' ')" }
}
Maven @("$versions`:set", "-DnewVersion=$Version", '-DgenerateBackupPoms=false')
Maven @("$versions`:set-property", '-Dproperty=alka.version', "-DnewVersion=$Version", '-DgenerateBackupPoms=false')

function Replace-InFile($path, $pattern, $replacement) {
    $text = [IO.File]::ReadAllText($path)
    $new = [regex]::Replace($text, $pattern, $replacement)
    if ($new -ne $text) { [IO.File]::WriteAllText($path, $new, (New-Object Text.UTF8Encoding($false))); "changed $path" }
}
$parent = '(?s)(<artifactId>alka-plugin-parent</artifactId>\s*<version>)[^<]+'
Replace-InFile 'archetype\src\main\resources\archetype-resources\pom.xml' $parent "`${1}$Version"
Replace-InFile 'README.md' $parent "`${1}$Version"
Replace-InFile 'README.md' '(-DarchetypeVersion=)\S+' "`${1}$Version"
"The version is $Version."
