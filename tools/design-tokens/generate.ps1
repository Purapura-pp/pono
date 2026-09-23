# Generate the design tokens from design/mockups/mock.css into both Pono themes and
# org.openpnp.gui.shell.Tokens. Run after changing a variable in the stylesheet; DesignTokensTest
# fails until you do.
#
#   tools\design-tokens\generate.ps1
$ErrorActionPreference = 'Stop'
. E:\pono-env\env.ps1 | Out-Null
$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repo
& mvn -o -q test-compile
if ($LASTEXITCODE -ne 0) { throw "mvn test-compile failed" }
& "$env:JAVA_HOME\bin\java.exe" -cp "target\test-classes" org.openpnp.gui.theme.DesignTokens
if ($LASTEXITCODE -ne 0) { throw "DesignTokens failed" }
