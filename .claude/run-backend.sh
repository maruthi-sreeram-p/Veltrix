#!/usr/bin/env bash
# Starts the backend against the local dev database backend/data/careerflux-run.
# Demo accounts are created on first start when secrets.demo-password exists
# (gitignored, one line). Without it, no demo accounts are made.
cd "$(dirname "$0")/../backend" || exit 1
if [ -f ../secrets.demo-password ]; then
  CAREERFLUX_DEMO_PASSWORD="$(tr -d '\r\n' < ../secrets.demo-password)"
  export CAREERFLUX_DEMO_PASSWORD
fi
export SPRING_DATASOURCE_URL="jdbc:h2:file:./data/careerflux-run;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE"
exec mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
