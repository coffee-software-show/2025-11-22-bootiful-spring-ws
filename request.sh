#!/usr/bin/env bash
curl --header "content-type: text/xml" -d @request.xml http://localhost:8080/ws
