
space :=
space +=
comma := ,

# Fully build and test the entire project including native executables
# Used by GitHub Action workflow test.yml
action-test-native:
	mvn --update-snapshots --no-transfer-progress install -Pnative
action-test-jvm:
	mvn --update-snapshots --no-transfer-progress install

# Deploy to particular environment (production, staging) with JVM or native executables
# Used by GitHub Action workflow deploy.yml
action-deploy-native-%:
	AWS_PROFILE=dataspray mvn clean deploy --update-snapshots --no-transfer-progress -Pnative,$*
action-deploy-jvm-%:
	AWS_PROFILE=dataspray mvn clean deploy --update-snapshots --no-transfer-progress -P$*

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
