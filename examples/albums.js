
var tlc = require('../');
var gql = require('graphql');
var labels = [
    {'id': 1, 'name': 'Apple Records', 'founded': '1968'},
    {'id': 2, 'name': 'Harvest Records', 'founded': '1969'}];
var albums = [
    {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973', 'artist': 'Pink Floyd', 'label': labels[1]},
    {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968', 'artist': 'The Beatles', 'label': labels[0]},
    {'id': 3, 'name': 'The Wall', 'releaseDate': 'August 1, 1982', 'artist': 'Pink Floyd', 'label': labels[1]}];
var dataResolver = {"query":  function (typename, predicate) {
  console.assert(typename == "Album");
  if (predicate == "all()") return albums;
  else {
    var predicates = predicate.split("&");
    var filters = predicates.map(function(p) {
      var [field, value] = p.split("=");
      var fields = field.split(".");
      if (fields.length == 2) {
          console.assert(fields[0] == "label");
          return function(elem) { return elem[fields[0]][fields[1]] == value; };
      }
      return function(elem) { return elem[field] == value; };
    });
    return albums.filter(function(elem) { return filters.every(function(f) { return f(elem); }); });
  }
}, "create": function (typename, inputs) {
  inputs.id = albums.length + 1;
  albums.push(inputs);
  return inputs;
}};
var schema = tlc.getSchema(dataResolver,
                           ["type Label { id: ID! name: String founded: String } ",
                            "type Album { id: ID! name: String releaseDate: String artist: String label: Label }"].join(" "));
var printer = function(res) { console.log(JSON.stringify(res, null, 2)); };
gql.graphql(schema,
  "{ Album(artist: \"Pink Floyd\", label: { name: \"Harvest Records\" }) { name artist releaseDate } }") .then(printer);
gql.graphql(schema, "{ Album(artist: \"Pink Floyd\", name: \"The Wall\") { name artist releaseDate } }").then(printer);
gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(printer);
gql.graphql(schema, "{ Albums { name artist releaseDate } }").then(printer);
gql.graphql(schema,
  "mutation m { createAlbum(name:\"The Division Bell\", releaseDate: \"March 28, 1994\", artist:\"Pink Floyd\") { id name } }")
    .then(printer);
