# RDS backup and restore runbook

## What is configured

Terraform configures automated backups for the PostgreSQL RDS instance using `rds_backup_retention_days`.
The default is 7 days.

## Verify backups

1. Open AWS Console.
2. Go to RDS.
3. Select the instance named `ticketing-platform-dev` unless you changed variables.
4. Check Backup retention period.
5. Check Latest restorable time.

## Restore test

A restore test should create a new temporary RDS instance from a point in time.
Do not restore over the active database in a training environment unless you intentionally want to reset it.

Example AWS CLI flow:

```powershell
aws rds restore-db-instance-to-point-in-time `
  --source-db-instance-identifier ticketing-platform-dev `
  --target-db-instance-identifier ticketing-platform-dev-restore-test `
  --use-latest-restorable-time
```

After restore:

1. Attach the restored instance to the same private subnet/security group setup if needed.
2. Temporarily point one service to the restored endpoint.
3. Verify the application can read expected data.
4. Delete the restored test instance to avoid cost.

## Failure decision

If RDS is down or saturated:

- Catalog reads can be partially protected by Redis cache.
- New reservations and orders should fail fast or degrade, not hang.
- Do not increase retry counts blindly; retries can amplify database failure.
