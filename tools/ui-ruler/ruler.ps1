# The UI ruler: photograph every page of the current build in the mockups' scene, pair each with
# its mockup, audit the screen, write report.md. Run from anywhere; it works in the repository.
#
#   tools\ui-ruler\ruler.ps1 [-Rounds base,small,hidpi] [-Scenes job,feeders] [-Build] [-Fixture] [-Mockups] [-Onscreen]
#
#   -Build     mvn package and test-compile first (otherwise the jar and test classes on disk are used)
#   -Fixture   write E:\pono-env\ui-fixture again (it is also written when it is missing)
#   -Mockups   render the mockups again (they are also rendered when missing)
#   -Onscreen  put the window on the screen, to watch it; by default it is kept off every screen and
#              the ruler runs behind whatever else is in front
#   -From      photograph another configuration directory than the fixture, a real machine's, with
#              -MachineOff when its controller is not there to be enabled
param(
    [string[]]$Rounds = @(),
    [string[]]$Scenes = @(),
    [string]$Out = "",
    [switch]$Build,
    [switch]$Fixture,
    [switch]$Mockups,
    [switch]$Onscreen,
    [string]$From = "",
    [switch]$MachineOff
)
$ErrorActionPreference = 'Stop'
. E:\pono-env\env.ps1 | Out-Null
$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repo

if ($Build) {
    & mvn -o -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
    & mvn -o -q test-compile
    if ($LASTEXITCODE -ne 0) { throw "mvn test-compile failed" }
}
$cp = "target\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar;target\test-classes"
$java = "$env:JAVA_HOME\bin\java.exe"
$jvm = @('--add-opens=java.base/java.lang=ALL-UNNAMED', '--add-opens=java.desktop/java.awt=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt.color=ALL-UNNAMED', '-Xmx2g', '-Dfile.encoding=UTF-8')

if ($Mockups -or -not (Test-Path 'E:\pono-env\ui-mockups\rendered')) {
    & (Join-Path $PSScriptRoot 'render-mockups.ps1')
}
if ($Fixture -or -not (Test-Path 'E:\pono-env\ui-fixture\machine.xml')) {
    & $java @jvm -cp $cp org.openpnp.gui.audit.UiFixture E:/pono-env/ui-fixture
    if ($LASTEXITCODE -ne 0) { throw "UiFixture failed" }
}

if (-not $Out) {
    $sha = (& git rev-parse --short HEAD).Trim()
    $Out = "E:\pono-env\ui-ruler\$(Get-Date -Format yyyyMMdd-HHmm)-$sha"
}
$rulerArgs = @('-cp', $cp, 'org.openpnp.gui.audit.UiRuler', '--out', $Out)
if ($Rounds) { $rulerArgs += @('--rounds', ($Rounds -join ',')) }
if ($Scenes) { $rulerArgs += @('--scenes', ($Scenes -join ',')) }
if ($Onscreen) { $rulerArgs += @('--onscreen', 'true') }
if ($From) { $rulerArgs += @('--fixture', $From) }
if ($MachineOff) { $rulerArgs += @('--machine', 'off') }
# No console window of its own either: nothing of the run comes to the front.
$p = Start-Process -FilePath $java -ArgumentList ($jvm + $rulerArgs) -WorkingDirectory $repo -PassThru -Wait `
    -NoNewWindow -RedirectStandardOutput "$env:TEMP\pono-ui-ruler.out" -RedirectStandardError "$env:TEMP\pono-ui-ruler.err"
Get-Content (Join-Path $Out 'ruler.log') -Tail 4
"exit $($p.ExitCode) -> $Out\report.md"
