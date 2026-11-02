#requires -Version 7.0
# Dot-source in a fresh interactive PowerShell 7 session. No database calls occur on load.
# Provides the four PostgreSQL commands used by WINDOWS_SUPABASE_BACKUP.md via Docker.
$InridePgImage = 'supabase/postgres@sha256:b15d862126dd2dac02da524696ccfde2dcce2ceaee0a58973b2727befe492706'
foreach ($InridePgTool in @('psql', 'pg_dump', 'pg_dumpall', 'pg_restore')) {
    if (Get-Command $InridePgTool -CommandType Function -ErrorAction SilentlyContinue) {
        throw 'A PostgreSQL function already exists. Use a fresh PowerShell 7 session; nothing was replaced.'
    }
}

function Invoke-InridePgClient {
    param([string] $Tool, [string[]] $ToolArgs)
    if ($Tool -notin @('psql', 'pg_dump', 'pg_dumpall', 'pg_restore')) { throw 'Unsupported PostgreSQL tool.' }
    if ($ToolArgs | Where-Object { $_ -match '(?i)(?:password\s*=|postgres(?:ql)?://)' }) {
        throw 'Connection credentials/URLs in arguments are forbidden. Use the documented masked password prompt.'
    }
    if ($ToolArgs.Count -eq 1 -and $ToolArgs[0] -eq '--version') {
        & docker run --rm --network none --entrypoint $Tool $InridePgImage --version
    } else {
        # These variables are initialized by the unmodified common backup setup block.
        $BackupRoot = Get-Variable Bundle -ValueOnly -ErrorAction Stop
        $BackupCa = Get-Variable CaFile -ValueOnly -ErrorAction Stop
        $BackupRoot = (Resolve-Path -LiteralPath $BackupRoot -ErrorAction Stop).ProviderPath
        $BackupCa = (Resolve-Path -LiteralPath $BackupCa -ErrorAction Stop).ProviderPath
        if ($BackupRoot -match '[,\r\n]' -or $BackupCa -match '[,\r\n]') { throw 'Docker mount paths must not contain commas/newlines.' }
        $MappedArgs = foreach ($Argument in $ToolArgs) {
            if ([IO.Path]::IsPathFullyQualified($Argument)) {
                $Relative = [IO.Path]::GetRelativePath($BackupRoot, $Argument)
                if ($Relative -ne '..' -and $Relative -notmatch '^\.\.[/\\]' -and -not [IO.Path]::IsPathFullyQualified($Relative)) {
                    '/backup/' + $Relative.Replace('\', '/')
                } else { $Argument }
            } else { $Argument }
        }
        $DockerArgs = @('run', '--rm')
        if ($Tool -eq 'pg_restore' -and $ToolArgs -contains '--list') { $DockerArgs += @('--network', 'none') }
        if ($ToolArgs -contains '--password') {
            if ([Console]::IsInputRedirected) { throw 'Run interactively in a terminal for the masked native password prompt.' }
            $DockerArgs += @('--interactive', '--tty')
        }
        $DockerArgs += @('--mount', "type=bind,source=$BackupRoot,target=/backup",
            '--mount', "type=bind,source=$BackupCa,target=/trust/database-root.crt,readonly",
            '--env', 'PGSSLMODE=verify-full', '--env', 'PGSSLROOTCERT=/trust/database-root.crt',
            '--entrypoint', $Tool, $InridePgImage)
        & docker @DockerArgs @MappedArgs
    }
    if ($LASTEXITCODE -ne 0) { throw "Docker PostgreSQL $Tool failed (exit $LASTEXITCODE); backup is not verified." }
}

function global:psql { Invoke-InridePgClient -Tool 'psql' -ToolArgs $args }
function global:pg_dump { Invoke-InridePgClient -Tool 'pg_dump' -ToolArgs $args }
function global:pg_dumpall { Invoke-InridePgClient -Tool 'pg_dumpall' -ToolArgs $args }
function global:pg_restore { Invoke-InridePgClient -Tool 'pg_restore' -ToolArgs $args }
Write-Output 'Docker PostgreSQL client mode loaded for this session only. No database connection or backup has run.'
