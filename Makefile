.PHONY: build doc test release

build:
	mvn -f com.pilosa.roaring/pom.xml clean package

doc:
	mvn -f com.pilosa.roaring/pom.xml javadoc:javadoc

test:
	mvn -f com.pilosa.roaring/pom.xml test

release:
	mvn -f com.pilosa.roaring/pom.xml clean deploy -P release
