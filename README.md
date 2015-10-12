# graphql-tlc

### A simpler interface to GraphQL

#### Install

* NPM - `npm install graphql-tlc` 

#### Motivation

GraphQL normally requires a `GraphQLSchema` object passed along with each query
you give it to validate, interpret & execute. Typically this schema is constructed
by hand-crafting some verbose & noisy JavaScript.

See: [starWarsSchema.js](https://github.com/graphql/graphql-js/blob/master/src/__tests__/starWarsSchema.js).

The equivalent schema in GraphQL Type Language is much more concise:
```
enum Episode { NEWHOPE, EMPIRE, JEDI }

type Human {
  id: ID!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  homePlanet: String
}

type Droid {
  id: ID!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  primaryFunction: String
}

union Character = Human | Droid
```

Given a specification of a data model in GraphQL Type Language, graphql-tlc automatically
generates the `GraphQLSchema` instance that GraphQL requires with
facilities for querying (i.e., selecting) & insert, update and delete mutations.

#### Example

```
node --harmony-destructuring

> var tlc = require('graphql-tlc');
> var gql = require('graphql');
> var albums = [
... {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973',
...   'artist': 'Pink Floyd'},
... {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968',
...   'artist': 'Beatles'}];
> var dataResolver = {"query":  function (typename, predicate) {
... console.assert(typename == "Album");
... var [field, value] = predicate.split("=");
... res = albums.filter(function(elem) { return elem[field] == value; });
... return res.length == 1 ? res[0] : res;
... }};
> var schema = tlc.getSchema(dataResolver,
... "type Album { id: ID! name: String releaseDate: String artist: String }");
> gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(
... function (res) { console.log(res); });

{ data: 
   { Album: 
      { name: 'The Beatles',
        artist: 'Beatles',
        releaseDate: 'November 22, 1968' } } }

```

Copyright (c) 2015 Jonathan L. Leonard
