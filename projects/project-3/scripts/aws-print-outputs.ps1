param(
    [string]$TerraformDir = "infra/aws/terraform"
)

$ErrorActionPreference = "Stop"
Push-Location $TerraformDir
terraform output
Pop-Location
