#!/usr/bin/env bash

curl -v --header "content-type: text/xml" -d @request-secure-3.xml http://localhost:8080/ws
