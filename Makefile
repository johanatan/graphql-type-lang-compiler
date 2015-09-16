.PHONY: all deps clean build test watch debug run

all: build

deps:
	lein deps

clean:
	lein clean

build:
	mkdir -p out/graphql-tlc/resources
	cp resources/* out/graphql-tlc/resources
	lein cljsbuild once main

ONCE_FLAG=once
test:
	mkdir -p out/test/graphql-tlc/resources
	cp resources/* out/test/graphql-tlc/resources
	lein doo node test $(ONCE_FLAG)

watch: ONCE_FLAG=
watch: test

DEBUG_FLAG=
debug: DEBUG_FLAG=--debug
debug: run

run: build
	cd out/frontend && node frontend.js $(DEBUG_FLAG)
