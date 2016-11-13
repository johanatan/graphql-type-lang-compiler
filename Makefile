.PHONY: all deps clean build test watch debug run

all: build

deps:
	lein deps

clean:
	lein clean

build:
	mkdir -p out/prod/resources
	cp resources/* out/prod/resources
	lein cljsbuild once main
	echo 'module.exports.getSchema = graphql_tlc.consumer.get_schema;' >> out/prod/graphql-tlc.js

ONCE_FLAG=once
test:
	mkdir -p out/test/resources
	cp resources/* out/test/resources
	lein doo node test $(ONCE_FLAG)

watch: ONCE_FLAG=
watch: test

DEBUG_FLAG=
debug: DEBUG_FLAG=--debug
debug: run

run: build
	cd out/prod/ && node graphql-tlc.js $(DEBUG_FLAG)

publish: clean build
	npm publish

