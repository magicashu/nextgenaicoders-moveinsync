#!/usr/bin/env sh
set -eu

trap 'kill 0' EXIT INT TERM
./mvnw -pl backend spring-boot:run &
npm run dev &
wait
