.PHONY: build test

build:
	mvn -f com.pilosa.roaring/pom.xml clean package

test:
	mvn -f com.pilosa.roaring/pom.xml test
