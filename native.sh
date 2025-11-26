#!/usr/bin/env bash
rm -rf target
./mvnw spring-javaformat:apply
./mvnw -DskipTests -Pnative native:compile
