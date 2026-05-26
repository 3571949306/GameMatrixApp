param(
    [string]$Action = "deploy",
    [string]$ModuleId = "",
    [string]$DexFile = "",
    [string]$Sha256 = ""
)

$HK_VPS = "root@149.104.29.181"
$SSH_KEY = "C:\Users\tcw\.ssh\id_ed25519"
$REMOTE_PATH = "/var/www/update"
$MODULE_SERVER_PATH = "/var/www/modules"
$MODULES_DIR = "deploy"

function Deploy-ModulesJson {
    Write-Host "=== 部署 modules.json 到 HK VPS ==="

    scp -o StrictHostKeyChecking=no "$MODULES_DIR\modules.json" "${HK_VPS}:${REMOTE_PATH}/modules.json"
    Write-Host "HK VPS: modules.json 已上传到 ${REMOTE_PATH}"

    # 同时上传到模块服务器目录
    scp -o StrictHostKeyChecking=no "$MODULES_DIR\modules.json" "${HK_VPS}:${MODULE_SERVER_PATH}/modules.json"
    Write-Host "HK VPS: modules.json 已上传到 ${MODULE_SERVER_PATH}"

    ssh -o StrictHostKeyChecking=no $HK_VPS "mkdir -p ${MODULE_SERVER_PATH}/${MODULES_DIR}; ls -la ${MODULE_SERVER_PATH}/modules.json ${MODULE_SERVER_PATH}/"
    Write-Host "HK VPS: 目录结构已确认"
}

function Deploy-ModuleDex {
    param([string]$Id, [string]$Dex, [string]$Hash)

    if (-not (Test-Path $Dex)) {
        Write-Host "错误: 文件不存在 - $Dex"
        return
    }

    Write-Host "=== 部署模块 $Id 到 HK VPS ==="
    Write-Host "文件: $Dex"
    Write-Host "SHA-256: $Hash"

    $fileName = Split-Path $Dex -Leaf

    # 上传到 update 服务器目录
    ssh -o StrictHostKeyChecking=no $HK_VPS "mkdir -p ${REMOTE_PATH}/modules"
    scp -o StrictHostKeyChecking=no $Dex "${HK_VPS}:${REMOTE_PATH}/modules/${fileName}"

    # 同时上传到模块服务器目录（9001端口）
    ssh -o StrictHostKeyChecking=no $HK_VPS "mkdir -p ${MODULE_SERVER_PATH}"
    scp -o StrictHostKeyChecking=no $Dex "${HK_VPS}:${MODULE_SERVER_PATH}/${fileName}"
    Write-Host "HK VPS: $fileName 已上传到两个目录"
}

function Show-Status {
    Write-Host "=== HK VPS 模块状态 ==="
    ssh -o StrictHostKeyChecking=no $HK_VPS "echo 'modules.json:'; cat ${REMOTE_PATH}/modules.json 2>/dev/null || echo '不存在'; echo ''; echo '模块文件:'; ls -la ${REMOTE_PATH}/modules/ 2>/dev/null || echo '目录不存在'"
}

switch ($Action) {
    "deploy" { Deploy-ModulesJson }
    "module" { Deploy-ModuleDex -Id $ModuleId -Dex $DexFile -Hash $Sha256 }
    "status" { Show-Status }
    default { Write-Host "用法: .\manage-modules.ps1 -Action [deploy|module|status]" }
}
