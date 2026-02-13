$mavenVersion = "3.9.6"
$mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
$installDir = "C:\Users\Lenovo\tools"
$mavenDir = "$installDir\apache-maven-$mavenVersion"
$zipFile = "$installDir\maven.zip"

Write-Host "Setting up installation directory..."
if (!(Test-Path $installDir)) {
    New-Item -ItemType Directory -Force -Path $installDir | Out-Null
}

if (Test-Path $mavenDir) {
    Write-Host "Maven already exists at $mavenDir"
} else {
    Write-Host "Downloading Maven $mavenVersion from $mavenUrl..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $mavenUrl -OutFile $zipFile
    } catch {
        Write-Error "Failed to download Maven. check internet connection."
        exit 1
    }

    Write-Host "Extracting Maven..."
    Expand-Archive -Path $zipFile -DestinationPath $installDir -Force

    Write-Host "Cleaning up..."
    Remove-Item $zipFile
}

Write-Host "Maven installed successfully."
Write-Host "Location: $mavenDir"

# Add to PATH for current session
$env:Path = "$mavenDir\bin;" + $env:Path

Write-Host "Verifying installation..."
& mvn -v

Write-Host "`nIMPORTANT: To make this permanent, add the following to your System PATH:"
Write-Host "$mavenDir\bin"
