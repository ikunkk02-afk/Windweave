$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$sourceInfo = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'audio-source.json') -Raw | ConvertFrom-Json
$sourcePath = Join-Path $projectPath 'work/audio/wind-whoosh-original.ogg'
$outputPath = Join-Path $projectPath 'src/main/resources/assets/windweave/sounds'
New-Item -ItemType Directory -Force -Path (Split-Path $sourcePath),$outputPath | Out-Null
if (-not (Test-Path -LiteralPath $sourcePath)) { Invoke-WebRequest -Uri $sourceInfo.url -OutFile $sourcePath }
if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sourceInfo.sha256) { throw 'Audio source checksum differs.' }
$loopFilter = 'asplit=3[mid][tail][head];[mid]atrim=start=0.5:end=5.459292,asetpts=PTS-STARTPTS[m];[tail]atrim=start=5.459292,asetpts=PTS-STARTPTS[t];[head]atrim=end=0.5,asetpts=PTS-STARTPTS[h];[t][h]acrossfade=d=0.5:c1=tri:c2=tri[f];[m][f]concat=n=2:v=0:a=1[out]'
# Filter before cutting the circular loop so filter startup cannot click at the loop seam.
& ffmpeg -hide_banner -loglevel error -y -i $sourcePath -filter_complex ('[0:a]highpass=f=70,lowpass=f=3500,volume=2.8,' + $loopFilter) -map '[out]' -ac 1 -ar 44100 -c:a libvorbis -q:a 5 (Join-Path $outputPath 'wind_loop.ogg')
if ($LASTEXITCODE -ne 0) { throw 'Wind conversion failed.' }
& ffmpeg -hide_banner -loglevel error -y -i $sourcePath -filter_complex ('[0:a]highpass=f=500,lowpass=f=8000,volume=5,' + $loopFilter) -map '[out]' -ac 1 -ar 44100 -c:a libvorbis -q:a 5 (Join-Path $outputPath 'sandstorm_loop.ogg')
if ($LASTEXITCODE -ne 0) { throw 'Sandstorm conversion failed.' }
