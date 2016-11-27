# RadLexXML
A utility for extracting terms from the RadLex OWL file for the TFS RadLex database.
To use this program, get the RadLex OWL file from the bioontology site for RadLex:
<p>
&nbsp;&nbsp;&nbsp;&nbsp;<a href="http://bioportal.bioontology.org/ontologies/RADLEX">http://bioportal.bioontology.org/ontologies/RADLEX</a>
<p>
Note: there are so many entities in the OWL file that the SAX parser hits the 64K default limit. The program internally expands
the limit to 256000 to get around the problem. This is the equivalent of running the program from the command line with this command:
<p>
&nbsp;&nbsp;&nbsp;&nbsp;java -Djdk.xml.entityExpansionLimit=256000 -jar RadLexXML.jar
<p>
If the OWL file grows over time to exceed the limit built into the program, you will have to change the first line in the initComponents method in RadLexXML.java to specify a new limit, since the code will overwrite anything you specify on the command line.
