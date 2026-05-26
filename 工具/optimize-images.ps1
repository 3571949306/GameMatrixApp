# 图片优化脚本 - 将 PNG 转换为 WebP 格式
# 用于减少 APK 体积，提升加载速度

param(
    [string]$DrawableDir = "app\src\main\res\drawable",
    [switch]$DryRun
)

Write-Host "🔍 开始优化图片..." -ForegroundColor Green
Write-Host "目标目录：$DrawableDir" -ForegroundColor Cyan

# 检查 cwebp 是否安装
$hasCwebp = $false
try {
    $cwebpVersion = & cwebp -version 2>$null
    if ($cwebpVersion) {
        $hasCwebp = $true
        Write-Host "✓ 检测到 cwebp: $cwebpVersion" -ForegroundColor Green
    }
} catch {
    Write-Host "⚠ 未检测到 cwebp 工具" -ForegroundColor Yellow
    Write-Host "请安装 WebP 工具：" -ForegroundColor Yellow
    Write-Host "  Windows: choco install webp" -ForegroundColor Gray
    Write-Host "  macOS:   brew install webp" -ForegroundColor Gray
    Write-Host "  Linux:   sudo apt-get install webp" -ForegroundColor Gray
}

# 需要转换的文件列表
$filesToConvert = @(
    @{Source="airplane.png"; Target="airplane.webp"; Quality=85},
    @{Source="comment.png"; Target="comment.webp"; Quality=80},
    @{Source="multiply.png"; Target="multiply.webp"; Quality=80}
)

$totalOriginalSize = 0
$totalOptimizedSize = 0

foreach ($file in $filesToConvert) {
    $sourcePath = Join-Path $DrawableDir $file.Source
    $targetPath = Join-Path $DrawableDir $file.Target
    
    if (-not (Test-Path $sourcePath)) {
        Write-Host "⊘ 跳过 $($file.Source) - 文件不存在" -ForegroundColor Gray
        continue
    }
    
    $originalSize = (Get-Item $sourcePath).Length
    $totalOriginalSize += $originalSize
    
    Write-Host "`n📝 处理：$($file.Source)" -ForegroundColor Cyan
    Write-Host "  原始大小：$([math]::Round($originalSize/1KB, 2)) KB" -ForegroundColor Yellow
    
    if ($DryRun) {
        Write-Host "  [DRY RUN] 将转换为 $($file.Target) (质量：$($file.Quality)%)" -ForegroundColor Gray
        continue
    }
    
    if ($hasCwebp) {
        # 使用 cwebp 转换
        $cwebpArgs = @(
            "-q", $file.Quality.ToString(),
            $sourcePath,
            "-o", $targetPath
        )
        
        Write-Host "  执行：cwebp $($cwebpArgs -join ' ')" -ForegroundColor Gray
        & cwebp @cwebpArgs
        
        if (Test-Path $targetPath) {
            $optimizedSize = (Get-Item $targetPath).Length
            $totalOptimizedSize += $optimizedSize
            $compressionRatio = [math]::Round((1 - $optimizedSize/$originalSize) * 100, 2)
            
            Write-Host "  ✓ 转换成功：$([math]::Round($optimizedSize/1KB, 2)) KB" -ForegroundColor Green
            Write-Host "  ✓ 压缩率：$compressionRatio%" -ForegroundColor Green
        } else {
            Write-Host "  ✗ 转换失败" -ForegroundColor Red
        }
    } else {
        Write-Host "  ⊘ 跳过转换（缺少 cwebp 工具）" -ForegroundColor Yellow
    }
}

# 处理启动图标（特殊处理，高质量）
$launcherSource = Join-Path $DrawableDir "ic_launcher_logo.png"
$launcherTarget = Join-Path $DrawableDir "ic_launcher_logo.webp"

if (Test-Path $launcherSource) {
    $launcherSize = (Get-Item $launcherSource).Length
    $totalOriginalSize += $launcherSize
    
    Write-Host "`n📝 处理：ic_launcher_logo.png (高质量)" -ForegroundColor Cyan
    Write-Host "  原始大小：$([math]::Round($launcherSize/1KB, 2)) KB ($([math]::Round($launcherSize/1MB, 2)) MB)" -ForegroundColor Yellow
    
    if (-not $DryRun -and $hasCwebp) {
        $cwebpArgs = @(
            "-q", "90",  # 启动图标使用更高质量
            $launcherSource,
            "-o", $launcherTarget
        )
        
        & cwebp @cwebpArgs
        
        if (Test-Path $launcherTarget) {
            $optimizedLauncherSize = (Get-Item $launcherTarget).Length
            $totalOptimizedSize += $optimizedLauncherSize
            $launcherCompressionRatio = [math]::Round((1 - $optimizedLauncherSize/$launcherSize) * 100, 2)
            
            Write-Host "  ✓ 转换成功：$([math]::Round($optimizedLauncherSize/1KB, 2)) KB ($([math]::Round($optimizedLauncherSize/1MB, 2)) MB)" -ForegroundColor Green
            Write-Host "  ✓ 压缩率：$launcherCompressionRatio%" -ForegroundColor Green
        }
    }
}

# 输出统计
Write-Host "`n" + "="*60 -ForegroundColor Cyan
Write-Host "📊 优化统计" -ForegroundColor Cyan
Write-Host "="*60 -ForegroundColor Cyan
Write-Host "原始总大小：$([math]::Round($totalOriginalSize/1KB, 2)) KB ($([math]::Round($totalOriginalSize/1MB, 2)) MB)" -ForegroundColor Yellow
Write-Host "优化后大小：$([math]::Round($totalOptimizedSize/1KB, 2)) KB ($([math]::Round($totalOptimizedSize/1MB, 2)) MB)" -ForegroundColor Green

if ($totalOptimizedSize -gt 0) {
    $totalCompressionRatio = [math]::Round((1 - $totalOptimizedSize/$totalOriginalSize) * 100, 2)
    Write-Host "总压缩率：$totalCompressionRatio%" -ForegroundColor Green
    Write-Host "节省空间：$([math]::Round(($totalOriginalSize - $totalOptimizedSize)/1KB, 2)) KB" -ForegroundColor Green
}

Write-Host "`n💡 提示:" -ForegroundColor Cyan
Write-Host "  • WebP 格式比 PNG 平均小 25-35%" -ForegroundColor Gray
Write-Host "  • Android 4.0+ 完全支持 WebP" -ForegroundColor Gray
Write-Host "  • 转换后可手动删除原始 PNG 文件" -ForegroundColor Gray
Write-Host "`n✅ 图片优化完成！" -ForegroundColor Green
