# Build a docker image locally and run it with docker-compose up
docker-run:
	sbt Docker/publishLocal
	docker-compose up
