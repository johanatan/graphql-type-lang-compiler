
var tlc = require('../');
var gql = require('graphql');
var albums = [
  {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973', 'artist': 'Pink Floyd'},
  {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968', 'artist': 'Beatles'}];
var dataResolver = {"query":  function (typename, predicate) {
  console.assert(typename == "Album");
  var [field, value] = predicate.split("=");
  res = albums.filter(function(elem) { return elem[field] == value; });
  return res.length == 1 ? res[0] : res;
}};
var schema = tlc.getSchema(dataResolver, "type Album { id: ID! name: String releaseDate: String artist: String }");
gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(function (res) { console.log(res); });

