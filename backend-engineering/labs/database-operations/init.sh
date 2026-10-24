#!/bin/sh
set -eu
# Sieć prywatnego laboratorium bez publikowania portów; hasło wyłącznie lokalne.
printf '\nhost replication postgres all scram-sha-256\n' >> "$PGDATA/pg_hba.conf"
