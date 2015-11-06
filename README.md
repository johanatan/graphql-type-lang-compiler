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
generates the `GraphQLSchema` instance that GraphQL requires and binds its `resolve` methods
to a specified set of functions for querying (i.e., selecting) and mutating (i.e., insert,
update and delete mutations).

#### Example

```javascript
$ node --harmony-destructuring
> var tlc = require('graphql-tlc');
> var gql = require('graphql');
> var albums = [
... {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973',
...   'artist': 'Pink Floyd'},
... {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968',
...   'artist': 'The Beatles'},
... {'id': 3, 'name': 'The Wall', 'releaseDate': 'Auguest 1, 1982',
...   'artist': 'Pink Floyd'}];
> var dataResolver = {"query":  function (typename, predicate) {
...   console.assert(typename == "Album");
...   if (predicate == "all()") return albums;
...   else {
...     var [field, value] = predicate.split("=");
...     var res = albums.filter(function(elem) { return elem[field] == value; });
...     return res.length == 1 ? res[0] : res;
...   }
... }, "create": function (typename, inputs) {
...   inputs.id = albums.length + 1;
...   albums.push(inputs);
...   return inputs;
... }};
> var schema = tlc.getSchema(dataResolver,
... "type Album { id: ID! name: String releaseDate: String artist: String }");
> var printer = function(res) { console.log(JSON.stringify(res, null, 2)); };
> gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(printer);

{
  "data": {
    "Album": {
      "name": "The Beatles",
      "artist": "The Beatles",
      "releaseDate": "November 22, 1968"
    }
  }
}

> gql.graphql(schema, "{ Albums { name artist releaseDate } }").then(printer);

{
  "data": {
    "Albums": [
      {
        "name": "Dark Side Of The Moon",
        "artist": "Pink Floyd",
        "releaseDate": "March 1, 1973"
      },
      {
        "name": "The Beatles",
        "artist": "The Beatles",
        "releaseDate": "November 22, 1968"
      },
      {
        "name": "The Wall",
        "artist": "Pink Floyd",
        "releaseDate": "Auguest 1, 1982"
      }
    ]
  }
}

> gql.graphql(schema, "mutation m { createAlbum(name:\"The Division Bell\", releaseDate: \"March 28, 1994\", artist:\"Pink Floyd\") { id name } }").then(printer);

{
  "data": {
    "createAlbum": {
      "id": "4",
      "name": "The Division Bell"
    }
  }
}

```

Copyright (c) 2015 Jonathan L. Leonard
