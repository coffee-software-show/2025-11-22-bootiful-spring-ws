#!/usr/bin/env bash
curl -v --header "content-type: text/xml" -d @request-secure-1.xml http://localhost:8080/ws
curl -v --header "content-type: text/xml" -d @request-secure-2.xml http://localhost:8080/ws
