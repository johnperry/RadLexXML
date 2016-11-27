package org.rsna.radlexxml;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.rsna.ui.ColorPane;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;

import org.w3c.dom.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A program to parse a RadLex OWL file, find all the terms
 * and write an XML file for use by TFS to build its RadLex
 * database. The database is used for finding terms in MIRCdocuments.
 *
 *The OWL file can be obtained from http://bioportal.bioontology.org/ontologies/RADLEX
 *
 *Note: With the recent OWL files, you must increase the entity expansion limit like this:
 *<code>java -Djdk.xml.entityExpansionLimit=256000 -jar RadLexXML.jar</code>
 */
public class RadLexXML extends JFrame {

	static Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

    TextPanel textPanel;
    FooterPanel footerPanel;
    String windowTitle = "RadLexXML - OWL Parser (SAX) - version 7";
	ColorPane text = new ColorPane();
	File file = new File("radlex.xml");
	File owlFile = null;
	LinkedList<RadLexElement> terms;
	LinkedList<OWLElement> stack;
	OWLElement currentElement = null;
	HashSet<String> qNames = new HashSet<String>();

    public static void main(String args[]) {
        new RadLexXML();
    }

    public RadLexXML() {
    	initComponents();
    }

    private void initComponents() {
		setTitle(windowTitle);
		JPanel main = new JPanel();
		main.setLayout(new BorderLayout());
		textPanel = new TextPanel();
		footerPanel = new FooterPanel();
		main.add(textPanel,BorderLayout.CENTER);
		main.add(footerPanel,BorderLayout.SOUTH);
		getContentPane().add(main, BorderLayout.CENTER);
		pack();
		centerFrame();
		this.setVisible(true);
		addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent evt) { System.exit(0); }
			}
		);
		owlFile = getOWLFile();
		(new ProcessingThread()).start();
	}

    private void centerFrame() {
		int width = 570;
		int height = 700;
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(width,height);
		setLocation(
			new Point(
				(scr.width - width)/2,
				(scr.height - height)/2));
    }

	private File getOWLFile() {
		File here = new File(System.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser(here);
		chooser.setDialogTitle("Select the RadLex OWL file");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		return null;
	}

	class FooterPanel extends JPanel {
		public JLabel message;
		public FooterPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout(FlowLayout.LEADING));
			this.setBackground(background);
			message = new JLabel(" ");
			this.add(message);
		}
		public void setMessage(String msg) {
			final String s = msg;
			Runnable display = new Runnable() {
				public void run() {
					message.setText(s);
				}
			};
			SwingUtilities.invokeLater(display);
		}
	}

	class TextPanel extends JPanel {
		public TextPanel() {
			super();
			setLayout(new BorderLayout());
        	text = new ColorPane();
			JScrollPane jsp = new JScrollPane();
			jsp.setViewportView(text);
			jsp.getViewport().setBackground(Color.white);
        	add(jsp, BorderLayout.CENTER);
		}
	}
	
	class ProcessingThread extends Thread {

		public ProcessingThread() { super(); }

		public void run() {
			text.setText("");
			try {
				long startTime = System.currentTimeMillis();
				terms = new LinkedList<RadLexElement>();
				stack = new LinkedList<OWLElement>();
				qNames = new HashSet<String>();

				//Parse the input OWL file
				SAXParserFactory factory = SAXParserFactory.newInstance();
				SAXParser saxParser = factory.newSAXParser();
				Handler handler = new Handler();
				footerPanel.setMessage("Parsing the OWL file...");
				saxParser.parse(owlFile, handler);
				
				footerPanel.setMessage("Sorting the terms...");
				RadLexElement[] rles = new RadLexElement[terms.size()];
				rles = terms.toArray(rles);
				Arrays.sort(rles);

				//Create the output doc
				footerPanel.setMessage("Creating the output document...");
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement("radlex");
				doc.appendChild(root);
				for (RadLexElement rle : rles) rle.appendTo(root);
				
				//Save the output doc
				footerPanel.setMessage("Saving the output document...");
				FileUtil.setText(file, XmlUtil.toPrettyString(doc));
				
				text.println(Color.black, "Elapsed time: "+(System.currentTimeMillis()-startTime) + " ms\n");
				int termCount = 0;
				int synCount = 0;
				int nenCount = 0;
				int obsCount = 0;
				Node child = root.getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						Element el = (Element)child;
						String type = el.getAttribute("type");
						if (type.equals("SYN")) synCount++;
						else if (type.equals("NEN")) nenCount++;
						else if (type.equals("OBS")) obsCount++;
						else termCount++;
					}
					child = child.getNextSibling();
				}
				text.println(Color.black, "List size                   = "+String.format("%7d",terms.size()));
				text.println(Color.black, "Number of RadLex terms      = "+String.format("%7d",termCount));
				text.println(Color.black, "Number of synonyms          = "+String.format("%7d",synCount));
				text.println(Color.black, "Number of non-English terms = "+String.format("%7d",nenCount));
				text.println(Color.black, "Number of obsolete terms    = "+String.format("%7d",obsCount));
				
				String[] qNamesArray = new String[qNames.size()];
				qNamesArray = qNames.toArray(qNamesArray);
				Arrays.sort(qNamesArray);
				text.println(Color.black, "\nElements with rdf:ID attributes found in the OWL file:");
				for (String qName : qNamesArray) {
					text.println(Color.black, qName);
				}
				
				footerPanel.setMessage("Done");
			}
			catch (Exception ex) {
				footerPanel.setMessage("Exception: "+ex.getMessage());
				StringWriter sw = new StringWriter();
				ex.printStackTrace(new PrintWriter(sw));
				text.println(Color.red, sw.toString());
				text.print(Color.black, "");
			}
		}
	}
	
	class Handler extends DefaultHandler {
		public Handler() {
			super();
		}
		public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
			if (currentElement != null) {
				stack.push(currentElement);
			}				
			currentElement = getOWLElement(currentElement, qName, attrs);
		}
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (currentElement != null) {
				currentElement.append(new String(ch, start, length));
			}
		}
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if ((currentElement != null) && (currentElement.parent != null)) {
				if ( !(currentElement instanceof RadLexElement) ) {
					if (currentElement.parent instanceof RadLexElement) {
						currentElement.parent.add(currentElement.qName, currentElement.textContent);
					}
				}
				else if (((RadLexElement)currentElement).hasContent()) {
					terms.add( (RadLexElement)currentElement );
				}
			}
			if (stack.size() > 0) currentElement = stack.pop();
		}
	}

	public OWLElement getOWLElement(OWLElement parent, String qName, Attributes attrs) {
		int index = attrs.getIndex("rdf:ID");
		if (index >= 0) {
			String id = attrs.getValue(index);
			if (id.startsWith("RID")) {
				qNames.add(qName);
				if (qName.endsWith("_metaclass") || qName.endsWith("Metaclass")) {
					return new RadLexElement(parent, qName, id);
				}
			}
		}
		return new OWLElement(parent, qName);
	}

	class OWLElement {
		public OWLElement parent = null;
		public String qName = "";
		public String textContent = "";
		public OWLElement(OWLElement parent, String qName) {
			this.parent = parent;
			this.qName = qName;
		}
		public void append(String text) {
			textContent += text;
		}
		public void add(String qName, String value) { }
	}
	
	class RadLexElement extends OWLElement implements Comparable<RadLexElement> {
		public String id;
		public int idInt = -1;
		public LinkedList<String> preferredNames;
		public LinkedList<String> obsoleteNames;
		public LinkedList<String> synonyms;
		public LinkedList<String> nonEnglishNames;
		public RadLexElement(OWLElement parent, String qName, String id) {
			super(parent, qName);
			this.id = id;
			preferredNames = new LinkedList<String>();
			obsoleteNames = new LinkedList<String>();
			synonyms = new LinkedList<String>();
			nonEnglishNames = new LinkedList<String>();
			try { idInt = Integer.parseInt(id.substring(3)); }
			catch (Exception ex) { }
		}
		public boolean hasContent() {
			return (preferredNames.size() > 0)
					|| (obsoleteNames.size() > 0)
					  || (synonyms.size() > 0)
					    || (nonEnglishNames.size() > 0);
		}
		public void add(String qName, String value) { 
			String qNameLC = qName.toLowerCase();
			if (qNameLC.equals("preferred_name")) {
				preferredNames.add(value.trim());
			}
			else if (qNameLC.equals("non_english_name")) {
				nonEnglishNames.add(value.trim());
			}
			else if (qNameLC.equals("synonym")) {
				synonyms.add(value.trim());
			}
			else if (qNameLC.equals("preferred_name_for_obsolete")) {
				obsoleteNames.add(value.trim());
			}				
		}
		public int compareTo(RadLexElement e) {
			return (idInt > e.idInt) ? 1 : ((idInt < e.idInt) ? -1 : 0);
		}
		public void appendTo(Element parent) {
			for (String s : preferredNames) {
				appendTo(parent, s, "");
			}
			for (String s : nonEnglishNames) {
				appendTo(parent, s, "NEN");
			}
			for (String s : synonyms) {
				appendTo(parent, s, "SYN");
			}
			for (String s : obsoleteNames) {
				appendTo(parent, s, "OBS");
			}
		}
		private void appendTo(Element parent, String name, String type) {
			Element child = parent.getOwnerDocument().createElement("term");
			child.setAttribute("id", id);
			if (!type.equals("")) child.setAttribute("type", type);
			child.setTextContent(name);
			parent.appendChild(child);
		}
	}
}
