

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
