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
	cd out/graphql-tlc && node graphql-tlc.js $(DEBUG_FLAG)

publish: clean build
	echo 'module.exports.getSchema = graphql_tlc.consumer.get_schema;' >> out/prod/graphql-tlc.js
	npm publish

