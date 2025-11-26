#!/usr/bin/env bash

find . -iname "pom.xml" | while read l ; do mvn -f $l spring-javaformat:apply ; done 

git commit -am 'polish' && git push 
