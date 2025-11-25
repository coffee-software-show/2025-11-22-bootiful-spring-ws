#!/usr/bin/env bash

./mvnw spring-javaformat:apply
git commit -am 'polish' && git push 
