
space :=
space +=
comma := ,

# Fully build and test the entire project including native executables
# Used by GitHub Action workflow test.yml
action-test:
	mvn install -Pnative

# Deploy to particular environment (production, staging) with JVM or native executables
# Used by GitHub Action workflow deploy.yml
action-deploy-cloud-native-%:
	AWS_PROFILE=dataspray mvn clean deploy -Pnative,$*
action-deploy-cloud-jvm-%:
	AWS_PROFILE=dataspray mvn clean deploy -P$*

# Deploy client libraries to package managers
client-languages := java typescript
action-deploy-client:
	mvn clean deploy -Pdeploy-client -am -pl dataspray-api/dataspray-api,$(subst $(space),$(comma),$(addprefix dataspray-api/dataspray-client-,${runner-languages}))

# Deploy runner libraries to package managers
runner-languages := java typescript
action-deploy-runner:
	mvn clean deploy -Pdeploy-runner -am -pl $(subst $(space),$(comma),$(addprefix dataspray-runner/dataspray-runner-,${runner-languages}))

deploy-control:
	make deploy-dataspray-stream-control
deploy-ingest:
	make deploy-dataspray-stream-ingest
deploy-backend:
	make deploy-dataspray-backend
deploy-%:
	mvn clean deploy -pl $* -am

install-cli:
	make install-dataspray-cli
install-%:
	mvn clean install -pl $* -am
