# Render the design mockups to PNG with headless Chrome, 1600 x 1000 CSS pixels at 1.5, which is
# the pixel size the UI ruler photographs the program at.
#
# Every page is rendered in both themes, as <base>.dark.png and <base>.light.png, the theme in the
# file name taken off the base: 01-workbench-job-dark.html gives 01-workbench-job.dark.png and
# 01-workbench-job.light.png. A page that shows both themes itself (06, whose right half carries
# the light variables) is rendered once, as <base>.both.png. The page as drawn, in its own theme,
# also goes to <base>.png one level up, where the reviewed mockups have always been.
#
#   tools\ui-ruler\render-mockups.ps1 [-Only 01] [-Out E:\pono-env\ui-mockups]
param(
    [string]$Only = "",
    [string]$Out = "E:\pono-env\ui-mockups"
)
$ErrorActionPreference = 'Stop'
$chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$src = Join-Path $PSScriptRoot "..\..\design\mockups" | Resolve-Path
$rendered = Join-Path $Out "rendered"
New-Item -ItemType Directory -Force $rendered | Out-Null
$profile = Join-Path $env:TEMP "pono-mock-chrome-profile"

function Render([string]$html, [string]$png, [string]$size = "1600,1000", [string]$scale = "1.5") {
    $url = "file:///" + ($html -replace '\\', '/')
    if (Test-Path $png) { Remove-Item $png }
    # Chrome reports on stderr, which Windows PowerShell turns into an error under 'Stop'.
    $ErrorActionPreference = 'Continue'
    & $chrome --headless=new --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check `
        --user-data-dir="$profile" --window-size=$size --force-device-scale-factor=$scale `
        --virtual-time-budget=1500 --screenshot="$png" $url 2>&1 | Out-Null
    $ErrorActionPreference = 'Stop'
    if (Test-Path $png) { Write-Output ("ok   " + (Split-Path $png -Leaf)) } else { Write-Output ("FAIL " + (Split-Path $png -Leaf)) }
}

# The board the UI fixture's top camera looks at: one image pixel per camera pixel.
Render (Join-Path $src "_camera.html") (Join-Path $rendered "camera-pcb.png") "1920,984" "1"

Get-ChildItem "$src\[0-9][0-9]-*.html" | ForEach-Object {
    if ($Only -and $_.Name -notlike "*$Only*") { return }
    $text = [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8)
    $base = $_.BaseName -replace '-(dark|light)$', ''
    Render $_.FullName (Join-Path $Out ($_.BaseName + ".png"))
    if ($text.Contains('th-light')) {
        Render $_.FullName (Join-Path $rendered "$base.both.png")
        return
    }
    foreach ($theme in 'dark', 'light') {
        # A sibling copy, so that mock.css, icons.js and scene.js resolve as they do for the page.
        $tmp = Join-Path $src "_render-$base-$theme.html"
        $themed = [regex]::Replace($text, '(<html[^>]*data-theme=")[a-z]+(")', "`${1}$theme`${2}", 1)
        [IO.File]::WriteAllText($tmp, $themed, (New-Object Text.UTF8Encoding($false)))
        try { Render $tmp (Join-Path $rendered "$base.$theme.png") } finally { Remove-Item $tmp }
    }
}
