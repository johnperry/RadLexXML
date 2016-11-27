# RadLexXML
A utility for extracting terms from the RadLex OWL file for the TFS RadLex database.
To use this program, go to the bioontology site for RadLex:<br><br>
<a href="http://bioportal.bioontology.org/ontologies/RADLEX">http://bioportal.bioontology.org/ontologies/RADLEX</a><br><br>
and get the RadLex OWL file.
<p>
Run this program in a command window with this command:
<p>
java -Djdk.xml.entityExpansionLimit=256000 -jar RadLexXML.jar
<p>
Note: there are so many entities in the OWL file that the SAX parser hits the default limit; that's why the -D parasmeter is required.
