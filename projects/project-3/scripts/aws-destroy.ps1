param(
    [string]$TerraformDir = "infra/aws/terraform",
    [string]$VarFile = "terraform.tfvars"
)

$ErrorActionPreference = "Stop"
Push-Location $TerraformDir
terraform destroy -var-file=$VarFile
Pop-Location
