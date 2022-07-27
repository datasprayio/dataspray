#!/bin/bash

cdk --profile dataspray --app "java -jar target/dataspray-dev-env-lambda-0.0.1-SNAPSHOT-jar-with-dependencies.jar test" $@

