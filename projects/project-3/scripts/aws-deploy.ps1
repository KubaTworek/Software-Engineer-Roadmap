param(
    [string]$TerraformDir = "infra/aws/terraform",
    [string]$VarFile = "terraform.tfvars"
)

$ErrorActionPreference = "Stop"
Push-Location $TerraformDir
terraform init
terraform apply -var-file=$VarFile
Pop-Location
