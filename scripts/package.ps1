param([ValidateSet('source','deployment')][string]$Kind='source')
$ErrorActionPreference='Stop'
# 固定本项目根目录；不枚举父仓库，也不复制本地口令、运行数据或数据库备份。
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$outputDirectory=Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$outputFile=Join-Path $outputDirectory ("sample-shop-$Kind.zip")
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream=[IO.File]::Open($outputFile,[IO.FileMode]::Create)
$archive=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
$count=0
try {
    foreach($item in Get-ChildItem -LiteralPath $projectRoot -Recurse -File -Force) {
        $relative=$item.FullName.Substring($projectRoot.Length+1).Replace('\','/')
        if($relative -match '(^|/)(node_modules|.runtime|.git)(/|$)'){continue}
        if($item.Name -like '.env*' -and $item.Name -ne '.env.example'){continue}
        if($item.Extension -eq '.log'){continue}
        if($Kind -eq 'source' -and $relative -match '(^|/)(target|dist)(/|$)'){continue}
        if($Kind -eq 'deployment'){
            $allowed=$relative -match '^(infra/|scripts/|docs/|README.md$|AGENTS.md$|.env.example$|.dockerignore$|.gitignore$|frontend/dist/)' -or $relative -match '^(shop-api|delivery-api)/target/[^/]+.jar$'
            if(-not $allowed){continue}
        }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$item.FullName,$relative,[IO.Compression.CompressionLevel]::Optimal)|Out-Null
        $count++
    }
} finally {$archive.Dispose();$stream.Dispose()}
Write-Output "$Kind package created: $count files; no .env or .runtime included."
Get-FileHash -LiteralPath $outputFile -Algorithm SHA256 | Select-Object Algorithm,Hash
