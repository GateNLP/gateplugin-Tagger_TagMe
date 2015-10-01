Simple tagger for tagging documents using the TagMe API.
See http://tagme.di.unipi.it/tagme_help.html

The current implementation does not handle errors very well and will just
produce a not very helpful exception that complains about an invalid response
when e.g. the API key is wrong or missing.

Note that TagMe unlike some other taggers may produce overlapping annotations sometimes
so some postprocessing may be needed to choose between them.

Also, TagMe does not actually create dbpedia URIs/IRIs, but instead returns
the WP title. We try to convert that title to a DBPedia URI but there may be at least
two potential problems:
= the encoding or the way how certain characters are represented may not be correct
= the generated URI may not correspond to the canonical DBPedia URI for that resource,
  e.g. because auf the DBPedia version not matching the WP version used by TagMe or 
  because a different way of redirecting to the "proper" WP page was used.


Quickly check the API from the command line:
  curl --data "key=<thekey>&text=Schumacher+won+the+race+in+Indianapolis" http://tagme.di.unipi.it/tag

NOTE: 

TagMe also provides two other API endpoints, one for spotting and one for calculating relatedness:
Spotting:
  curl --data "key=<thekey>&text=Schumacher+won+the+race+in+Indianapolis" http://tagme.di.unipi.it/spot
Relatedness
  curl --data "key=<thekey>&tt=Barack_Obama United_Kingdom" http://tagme.di.unipi.it/rel
These two are not supported at the moment.
