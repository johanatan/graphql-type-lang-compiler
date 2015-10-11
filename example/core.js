
var tlc = require("../");
console.log(tlc.getSchema({"query": function(typename, predicate) { return null; }}, "type Color { id: ID! name: String }"));

