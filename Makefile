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

test:
	lein doo node test once

watch:
	lein doo node test

DEBUG_FLAG=
debug: DEBUG_FLAG=--debug
debug: run

run: build
	cd out/frontend && node frontend.js $(DEBUG_FLAG)
