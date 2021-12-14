#!/bin/bash
clear
echo "Running Server"

cd datastax-tickdb

mvn clean compile package install -DskipTests

mvn jetty:run -Djetty.port=7001
