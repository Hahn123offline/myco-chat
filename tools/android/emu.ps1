# 模拟器自验脚本（P4 反复使用）
#
# 用法：
#   pwsh -File tools\android\emu.ps1 -Action status
#   pwsh -File tools\android\emu.ps1 -Action build
#   pwsh -File tools\android\emu.ps1 -Action install
#   pwsh -File tools\android\emu.ps1 -Action launch -Entry gallery
#   pwsh -File tools\android\emu.ps1 -Action shot  -Name home
#   pwsh -File tools\android\emu.ps1 -Action suite          # 一轮标准截图：gallery/sample/chat/设置
#   pwsh -File tools\android\emu.ps1 -Action boot           # 无窗口启动模拟器（若未运行）
#   pwsh -File tools\android\emu.ps1 -Action stop
#
# 产物：_ws\out\shots\<时间戳>\*.png
param(
  [Parameter(Mandatory=$true)][ValidateSet('status','boot','stop','build','install','launch','shot','suite','input','rotate')]
  [string]$Action,
  [string]$Entry = 'chat',            # gallery | sample | chat | settings
  [string]$Name  = 'shot',
  [string]$Text  = '',
  [string]$Keys  = '',                # 例：'KEYCODE_BACK' 或 'KEYCODE_ENTER'
  [int]$WaitMs   = 1200,
  [switch]$Dark
)

$ErrorActionPreference = 'Continue'
$root  = 'E:\myco-chat'
$sdk   = 'C:\Users\11096\AppData\Local\Android\Sdk'
$adb   = Join-Path $sdk 'platform-tools\adb.exe'
$emu   = Join-Path $sdk 'emulator\emulator.exe'
$avd   = 'myco35'
$apk   = Join-Path $root 'app-android\build\outputs\apk\debug\app-android-debug.apk'
$pkg   = 'dev.myco.android'
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk

function Get-Serial {
  $lines = & $adb devices 2>&1 | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
  if (-not $lines) { return $null }
  return (($lines | Select-Object -First 1) -split '\s+')[0]
}

function Get-ShotDir {
  $day = Join-Path $root '_ws\out\shots'
  New-Item -ItemType Directory -Force $day | Out-Null
  return $day
}

switch ($Action) {
  'status' {
    & $adb devices 2>&1
    $s = Get-Serial
    if ($s) {
      "serial=$s"
      "boot_completed=$(& $adb -s $s shell getprop sys.boot_completed 2>&1)"
      "api=$(& $adb -s $s shell getprop ro.build.version.sdk 2>&1)"
      "size=$(& $adb -s $s shell wm size 2>&1)"
      "installed=$(& $adb -s $s shell pm list packages $pkg 2>&1)"
    } else { 'no device' }
  }

  'boot' {
    if (Get-Serial) { 'already running'; break }
    Start-Process -FilePath $emu -ArgumentList @(
      '-avd', $avd, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
      '-gpu', 'swiftshader_indirect', '-camera-back', 'none', '-camera-front', 'none',
      '-netdelay', 'none', '-netspeed', 'full'
    ) -WindowStyle Hidden
    "booting $avd ..."
    & $adb wait-for-device 2>&1 | Out-Null
    for ($i = 0; $i -lt 90; $i++) {
      $b = (& $adb shell getprop sys.boot_completed 2>&1) -join ''
      if ($b.Trim() -eq '1') { "booted in ~$($i*2)s"; break }
      Start-Sleep -Seconds 2
    }
  }

  'stop' {
    & $adb emu kill 2>&1 | Out-Null
    'stopped'
  }

  'build' {
    & 'E:\11-15\tools\gradle-8.9\bin\gradle.bat' -p $root ':app-android:assembleDebug' --console=plain -q
    if ($LASTEXITCODE -eq 0) { "build OK -> $apk" } else { "build FAILED exit=$LASTEXITCODE" }
  }

  'install' {
    $s = Get-Serial; if (-not $s) { 'no device'; exit 1 }
    & $adb -s $s install -r $apk 2>&1 | Select-Object -Last 2
  }

  'launch' {
    $s = Get-Serial; if (-not $s) { 'no device'; exit 1 }
    $extra = switch ($Entry) {
      'gallery' { '--ez m5_gallery true' }
      'sample'  { '--ez m5_chat true' }
      'chat'    { '--ez m5_chat_ui true' }
      'settings'{ '' }
      default   { '' }
    }
    & $adb -s $s shell am force-stop $pkg 2>&1 | Out-Null
    $cmd = "am start -n $pkg/.MainActivity $extra"
    & $adb -s $s shell $cmd 2>&1 | Select-Object -First 2
    Start-Sleep -Milliseconds $WaitMs
  }

  'shot' {
    $s = Get-Serial; if (-not $s) { 'no device'; exit 1 }
    $dir = Get-ShotDir
    $path = Join-Path $dir ("{0}-{1}.png" -f (Get-Date -Format 'HHmmss'), $Name)
    cmd /c "`"$adb`" -s $s exec-out screencap -p > `"$path`""
    "shot -> $path ($((Get-Item $path).Length) bytes)"
  }

  'suite' {
    $s = Get-Serial; if (-not $s) { 'no device'; exit 1 }
    $dir = Get-ShotDir
    $stamp = Get-Date -Format 'MMdd-HHmm'
    foreach ($e in @('gallery','sample','chat')) {
      & $adb -s $s shell am force-stop $pkg 2>&1 | Out-Null
      $extra = switch ($e) { 'gallery' {'--ez m5_gallery true'} 'sample' {'--ez m5_chat true'} 'chat' {'--ez m5_chat_ui true'} }
      & $adb -s $s shell ("am start -n $pkg/.MainActivity $extra") 2>&1 | Out-Null
      Start-Sleep -Milliseconds 5000
      $p = Join-Path $dir "$stamp-$e.png"
      cmd /c "`"$adb`" -s $s exec-out screencap -p > `"$p`""
      "  $p"
    }
    'suite done'
  }

  'input' {
    $s = Get-Serial; if (-not $s) { 'no device'; exit 1 }
    if ($Text) { & $adb -s $s shell input text $Text 2>&1 | Out-Null }
    if ($Keys) { foreach ($k in ($Keys -split ',')) { & $adb -s $s shell input keyevent $k 2>&1 | Out-Null } }
    Start-Sleep -Milliseconds $WaitMs
    'input sent'
  }

  'rotate' {
    $s = Get-Serial; if (-not $s) { 'no device'; exit 1 }
    & $adb -s $s shell settings put system accelerometer_rotation 0 2>&1 | Out-Null
    & $adb -s $s shell settings put system user_rotation 1 2>&1 | Out-Null
    Start-Sleep -Milliseconds 1500
    'rotated to landscape (use user_rotation 0 to restore)'
  }
}
