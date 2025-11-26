#!/usr/bin/env bash
mvn  -f ../model/pom.xml  spring-javaformat:apply  clean  install
mvn  -f ../aot/pom.xml  spring-javaformat:apply  clean  install
./mvnw spring-javaformat:apply clean spring-boot:run