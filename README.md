# RadLexXML
A utility for extracting terms from the RadLex OWL file for the TFS RadLex database.
To use this program, get the RadLex OWL file from the bioontology site for RadLex:
<p>
&nbsp;&nbsp;&nbsp;&nbsp;<a href="http://bioportal.bioontology.org/ontologies/RADLEX">http://bioportal.bioontology.org/ontologies/RADLEX</a>
<p>
Note: there are so many entities in the OWL file that the SAX parser hits the 64K default limit. The program internally expands
the limit to 256000. This the equivalent of running the program from the command line with this command:
<p>
&nbsp;&nbsp;&nbsp;&nbsp;java -Djdk.xml.entityExpansionLimit=256000 -jar RadLexXML.jar</tt>
