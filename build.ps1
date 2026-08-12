# Build helper.
#
# Three environment quirks on this machine are baked in here so they don't have to be
# rediscovered:
#
#  1. TEMP must be a short local path. Gradle's daemon uses java.nio.channels.Pipe, which
#     on Windows opens an AF_UNIX socket file inside TEMP. Under the default temp path the
#     forked daemon dies with "Unable to establish loopback connection" (visible as a
#     UnixDomainSockets.connect failure in .gradle-home/daemon/*/daemon-*.out.log).
#     This is the load-bearing fix — without it every build fails in about 5 seconds.
#  2. GRADLE_USER_HOME is kept local and project-scoped, so builds don't depend on the
#     roaming profile.
#  3. There is no `gradle` on PATH and no wrapper jar, so the downloaded distribution
#     under .tooling is invoked directly.
#
# Usage:  .\build.ps1 test
#         .\build.ps1 assembleDebug
#         .\build.ps1 installDebug
param([Parameter(ValueFromRemainingArguments = $true)] $Tasks)

if (-not $Tasks) { $Tasks = @("testDebugUnitTest") }

New-Item -ItemType Directory -Force -Path "C:\gtmp" | Out-Null
$env:TEMP = "C:\gtmp"
$env:TMP = "C:\gtmp"

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "C:\Projects\android-sdk"
$env:GRADLE_USER_HOME = "C:\Projects\.gradle-home"
Remove-Item Env:\GRADLE_OPTS -ErrorAction SilentlyContinue

# Pin installs to one device. Without this, a stale emulator entry makes installDebug
# ambiguous and it fails with "more than one device/emulator".
# Set BP_DEVICE_SERIAL in your environment (`adb devices` to find it), or leave it unset
# when only one device is attached. Not hardcoded — a device serial is an identifier and
# this file is public.
if ($env:BP_DEVICE_SERIAL) { $env:ANDROID_SERIAL = $env:BP_DEVICE_SERIAL }

& "$PSScriptRoot\.tooling\gradle-8.11.1\bin\gradle.bat" -p $PSScriptRoot @Tasks --console=plain
