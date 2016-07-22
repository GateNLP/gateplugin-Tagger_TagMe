gateplugin-Tagger_TagMe
=======================

A plugin for the <a href="https://gate.ac.uk">GATE language technology</a> 
framework that provides a PR for annotating/tagging documents using the
<a href="https://tagme.d4science.org/tagme/tagme_help.html">TagMe API</a>.

For more information please consult the Wiki: 
https://github.com/johann-petrak/gateplugin-Tagger_TagMe/wiki

Notes:

* Currently only the endpoint for tagging is supported 
* TagMe unlike some other taggers may sometimes produce overlapping annotations so some postprocessing may be needed to choose between them. 
* TagMe does currently not create DBpedia URIs/IRIs, but instead returns
the WP title. The PR tries to convert that title to a DBPedia URI but this may fail in at least two ways:
  * the encoding or the way how certain characters are represented may not be correct
  * the generated URI may not correspond to the canonical DBPedia URI for that resource, e.g. because auf the DBPedia version not matching the WP version used by TagMe or because a different way of redirecting to the "proper" WP page was used.

