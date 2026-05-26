$bytes = [System.IO.File]::ReadAllBytes('d:\kaifa\GameCenterApp\PROJECT_CONTEXT.md')
$hex = ($bytes[0..20] | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
Write-Host "First 21 bytes hex: $hex"

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$text = $utf8NoBom.GetString($bytes)
Write-Host "UTF8 no BOM first 100: $($text.Substring(0, [Math]::Min(100, $text.Length)))"

$utf8Bom = New-Object System.Text.UTF8Encoding($true)
$text2 = $utf8Bom.GetString($bytes)
Write-Host "UTF8 with BOM first 100: $($text2.Substring(0, [Math]::Min(100, $text2.Length)))"

$gbk = [System.Text.Encoding]::GetEncoding('GBK')
$text3 = $gbk.GetString($bytes)
Write-Host "GBK first 100: $($text3.Substring(0, [Math]::Min(100, $text3.Length)))"
