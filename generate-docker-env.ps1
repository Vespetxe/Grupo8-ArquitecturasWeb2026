#Requires -Version 5.1
[CmdletBinding()]
param()

# Disable PowerShell statement/assignment tracing before handling any secrets.
Set-PSDebug -Off
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$envPath = Join-Path -Path $PSScriptRoot -ChildPath '.env'
$failureMessage = 'No se pudieron preparar las credenciales. No se mostraran detalles sensibles.'
$stream = $null
$rng = $null
$randomBytes = $null
$payload = $null
$password = $null
$lines = [System.Collections.Generic.List[string]]::new()
$unique = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

try {
    $failureMessage = 'Este generador requiere PowerShell en Windows con soporte para ACL de archivos.'
    if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
        throw 'Unsupported platform.'
    }

    $failureMessage = '.env ya existe. No se sobrescribio ni se cambio su contenido o sus permisos.'
    if (Test-Path -LiteralPath $envPath) {
        throw 'Existing destination.'
    }

    $failureMessage = 'No se pudo verificar que .env este ignorado por Git y fuera del indice.'
    $git = Get-Command git -CommandType Application -ErrorAction Stop | Select-Object -First 1
    $tracked = @(& $git.Source -C $PSScriptRoot ls-files -- .env 2>$null)
    if ($LASTEXITCODE -ne 0 -or $tracked.Count -ne 0) {
        throw 'Git index check failed.'
    }
    & $git.Source -C $PSScriptRoot check-ignore --quiet --no-index -- .env 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'Git ignore check failed.'
    }

    $failureMessage = 'No se pudieron generar cinco contrasenas hexadecimales validas y distintas.'
    $variableNames = @(
        'TY_E2_MYSQL_ROOT_PASSWORD'
        'TY_E2_EVENTOS_DB_PASSWORD'
        'TY_E2_INVENTARIO_DB_PASSWORD'
        'TY_E2_ENTRADAS_DB_PASSWORD'
        'TY_E2_COMPRAS_DB_PASSWORD'
    )
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    foreach ($variableName in $variableNames) {
        $randomBytes = [byte[]]::new(32)
        $rng.GetBytes($randomBytes)
        $password = [System.BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
        [System.Array]::Clear($randomBytes, 0, $randomBytes.Length)
        if ($password -cnotmatch '\A[0-9a-f]{64}\z' -or -not $unique.Add($password)) {
            throw 'Credential validation failed.'
        }
        $lines.Add($variableName + '=' + $password)
        $password = $null
    }
    if ($lines.Count -ne 5 -or $unique.Count -ne 5) {
        throw 'Credential count check failed.'
    }

    $failureMessage = 'No se pudo preparar una ACL exclusiva para el usuario actual.'
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    try { $currentSid = $identity.User } finally { $identity.Dispose() }
    $security = [System.Security.AccessControl.FileSecurity]::new()
    $security.SetAccessRuleProtection($true, $false)
    $security.SetOwner($currentSid)
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $currentSid,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        [System.Security.AccessControl.AccessControlType]::Allow
    )
    $security.AddAccessRule($rule)
    $rights = [System.Security.AccessControl.FileSystemRights]::Read -bor
        [System.Security.AccessControl.FileSystemRights]::Write

    # CreateNew also refuses a destination created after the initial existence check.
    # Supply the ACL at creation, before any credential bytes reach the file.
    $failureMessage = 'No se pudo crear .env de forma exclusiva y protegida. No se sobrescribio ningun archivo existente.'
    if ($PSVersionTable.PSEdition -eq 'Desktop') {
        $stream = [System.IO.FileStream]::new(
            $envPath, [System.IO.FileMode]::CreateNew, $rights,
            [System.IO.FileShare]::None, 4096, [System.IO.FileOptions]::None, $security
        )
        $actualSecurity = $stream.GetAccessControl()
    } else {
        $stream = [System.IO.FileSystemAclExtensions]::Create(
            [System.IO.FileInfo]::new($envPath), [System.IO.FileMode]::CreateNew, $rights,
            [System.IO.FileShare]::None, 4096, [System.IO.FileOptions]::None, $security
        )
        $actualSecurity = [System.IO.FileSystemAclExtensions]::GetAccessControl($stream)
    }

    $failureMessage = 'No se pudo confirmar la ACL restrictiva. Puede haber quedado un .env vacio; revisar antes de reintentar.'
    $rules = @($actualSecurity.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]))
    $owner = $actualSecurity.GetOwner([System.Security.Principal.SecurityIdentifier])
    if (-not $actualSecurity.AreAccessRulesProtected -or $owner.Value -ne $currentSid.Value -or
        $rules.Count -ne 1) {
        throw 'ACL validation failed.'
    }
    if ($rules[0].IdentityReference.Value -ne $currentSid.Value -or
        $rules[0].AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow -or
        $rules[0].FileSystemRights -ne [System.Security.AccessControl.FileSystemRights]::FullControl) {
        throw 'ACL rule validation failed.'
    }

    $failureMessage = 'No se completo la escritura de .env. Puede haber quedado un archivo parcial protegido; revisar antes de reintentar.'
    $encoding = [System.Text.UTF8Encoding]::new($false, $true)
    $payload = $encoding.GetBytes(($lines -join "`n") + "`n")
    $stream.Write($payload, 0, $payload.Length)
    $stream.Flush($true)
} catch {
    # Do not propagate native/.NET exception details or credential values.
    throw [System.InvalidOperationException]::new($failureMessage)
} finally {
    if ($null -ne $stream) { $stream.Dispose() }
    if ($null -ne $rng) { $rng.Dispose() }
    if ($null -ne $randomBytes) { [System.Array]::Clear($randomBytes, 0, $randomBytes.Length) }
    if ($null -ne $payload) { [System.Array]::Clear($payload, 0, $payload.Length) }
    $lines.Clear()
    $unique.Clear()
    $password = $null
}

Write-Host '.env creado con cinco credenciales independientes y permisos exclusivos para el usuario actual. No se mostraron secretos.'
