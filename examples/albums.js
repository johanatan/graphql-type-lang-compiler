
var tlc = require('../');
var gql = require('graphql');
var albums = [
  {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973', 'artist': 'Pink Floyd'},
  {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968', 'artist': 'The Beatles'},
  {'id': 3, 'name': 'The Wall', 'releaseDate': 'August 1, 1982', 'artist': 'Pink Floyd'}];
var dataResolver = {"query":  function (typename, predicate) {
  console.assert(typename == "Album");
  if (predicate == "all()") return albums;
  else {
    var [field, value] = predicate.split("=");
    var res = albums.filter(function(elem) { return elem[field] == value; });
    return res.length == 1 ? res[0] : res;
  }
}, "create": function (typename, inputs) {
  inputs.id = albums.length + 1;
  albums.push(inputs);
  return inputs;
}};
var schema = tlc.getSchema(dataResolver, "type Album { id: ID! name: String releaseDate: String artist: String }");
var printer = function(res) { console.log(JSON.stringify(res, null, 2)); };
gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(printer);
gql.graphql(schema, "{ Albums { name artist releaseDate } }").then(printer);
gql.graphql(schema, "mutation m { createAlbum(name:\"The Division Bell\", releaseDate: \"March 28, 1994\", artist:\"Pink Floyd\") { id name } }").then(printer);
