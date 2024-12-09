
space :=
space +=
comma := ,

# Fully build and test the entire project including native executables
# Used by GitHub Action workflow test.yml
action-test:
	mvn --update-snapshots --no-transfer-progress install -Pnative

# Deploy to particular environment (production, staging) with JVM or native executables
# Used by GitHub Action workflow deploy.yml
action-deploy-cloud-native-%:
	AWS_PROFILE=dataspray mvn --update-snapshots --no-transfer-progress clean deploy -Pnative,$*
action-deploy-cloud-jvm-%:
	AWS_PROFILE=dataspray mvn --update-snapshots --no-transfer-progress clean deploy -P$*

# Deploy client libraries to package managers
client-languages := java typescript
action-deploy-client: $(addprefix action-deploy-client-,$(client-languages))
action-deploy-client-%:
	mvn clean deploy --update-snapshots --no-transfer-progress -am -pl dataspray-api,dataspray-client-parent/dataspray-client-$*

# Deploy runner libraries to package managers
runner-languages := java typescript
action-deploy-runner: $(addprefix action-deploy-runner-,$(runner-languages))
action-deploy-runner-%:
	mvn clean deploy --update-snapshots --no-transfer-progress -am -pl dataspray-runner-parent/dataspray-runner-$*

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
