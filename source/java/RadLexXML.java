package org.rsna.radlexxml;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.LinkedList;
import javax.swing.border.BevelBorder;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A program to walk the RadLex site, find all the terms
 * and write an XML file for use by TFS to build its RadLex
 * database. The database is used for finding terms in MIRCdocuments.
 */
public class RadLexXML extends JFrame implements ActionListener {

	static Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	static final String url = "http://rest.bioontology.org/bioportal/virtual/ontology/";
	static final String query = "?light=1&apikey=04ef5317-efb0-4179-97eb-f80f7598e93e&conceptid=";

    TextPanel		textPanel;
    HeaderPanel		headerPanel;
    FooterPanel		footerPanel;
    String			windowTitle = "RadLexXML - Bioportal - version 4";
    int 			width = 570;
    int 			height = 700;
    int				termCount = 0;
    SkipNodes		skipNodes;
    HashSet<String>	seenNodes;
    StringBuffer	errors;
    JButton			go = new JButton("GO");
	ColorPane		text = new ColorPane();
	File			file = new File("radlex.xml");
	String			ontologyID = "1057";
	boolean			showErrors = true;
	int 			maxTerms = 0;

    public static void main(String args[]) {
        new RadLexXML();
    }

    public RadLexXML() {
    	initComponents();
    	go.addActionListener(this);
    	skipNodes = new SkipNodes();
    	seenNodes = new HashSet<String>();
    	errors = new StringBuffer();
    }

    public void actionPerformed(ActionEvent event) {
		ontologyID = headerPanel.oID.getText();
		showErrors = headerPanel.show.isSelected();
		try { maxTerms = Integer.parseInt(headerPanel.termLimit.getText()); }
		catch (Exception ex) {
			maxTerms = 0;
			headerPanel.termLimit.setText("0");
		}
		(new TermGetter()).start();
		footerPanel.setMessage("Working...");
	}

	class TermGetter extends Thread {
		public TermGetter() { super(); }
		public void run() { getTerms(); }
	}

    private void initComponents() {
		setTitle(windowTitle);
		JPanel main = new JPanel();
		main.setLayout(new BorderLayout());
		headerPanel = new HeaderPanel();
		textPanel = new TextPanel();
		footerPanel = new FooterPanel();
		main.add(headerPanel,BorderLayout.NORTH);
		main.add(textPanel,BorderLayout.CENTER);
		main.add(footerPanel,BorderLayout.SOUTH);
		getContentPane().add(main, BorderLayout.CENTER);
		pack();
		centerFrame();
		this.setVisible(true);
		addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent evt) { exitApp(); }
			}
		);
   }

	public void getTerms() {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.newDocument();
			Element root = doc.createElement("radlex");
			doc.appendChild(root);

			text.setText("");
			long startTime = System.currentTimeMillis();
			termCount = 0;
			addTerm(root, "RID0");
			setFileText(file, utf8, xmlString(doc));

			text.print(Color.black, "\nCount = "+termCount+"\n");
			text.print(Color.red, "Elapsed time: "+(System.currentTimeMillis()-startTime) + "\n\n");
			footerPanel.setMessage("Done");
			if (errors.length() > 0) {
				text.print(Color.red, "Errors:\n");
				text.print(Color.red, errors.toString());
			}
		}
		catch (Exception ex) {
			footerPanel.setMessage("Exception: "+ex.getMessage());
			ex.printStackTrace();
		}
	}

	//Add a term and all its descendants to the root element
	private void addTerm(Element root, String id) {
		try {
			if ((maxTerms > 0) && (termCount >= maxTerms)) return;
			if (!seenNodes.contains(id) && id.startsWith("RID")) {
				NodeList nl;
				Term term = getTerm(id);
				if (!skipNodes.skip(term)) {
					appendTerm(root, term.id, term.text);
					termCount++;
					text.print(Color.black, termCount + ": " + id + ": "+ term.text + "\n");

					for (String s : term.synonyms) {
						appendTerm(root, term.id, s);
						text.print(Color.blue, "                " + s + "\n");
					}

					seenNodes.add(id);

					for (String s : term.children) {
						addTerm(root, s);
					}
				}
			}
		}
		catch (Exception ex) {
			System.out.println("Term "+id+": "+ex.getClass().getName()+": "+ex.getMessage());
			ex.printStackTrace();
		}
	}

	private void appendTerm(Element element, String id, String text) {
		Element child = element.getOwnerDocument().createElement("term");
		child.setAttribute("id", id);
		child.setTextContent(text);
		element.appendChild(child);
	}

	//Get a term from the radlex.org site
	private Term getTerm(String id) {
		String address = url + ontologyID + query + id;
		String content = "";
		try {
			content = connect(address);
			StringReader sr = new StringReader(content);
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = db.parse(new InputSource(sr));
			return new Term(doc.getDocumentElement());
		}
		catch (Exception skip) {
			System.out.println("Exception while getting term: "+id);
			footerPanel.setMessage("Exception while getting term: "+id);
			errors.append(id + "\n");
			if (showErrors) {
				text.print(Color.red, "Exception while getting term: "+id+"\n");
				text.print(Color.red, content);
				text.print(Color.black, "\n\n");
			}
		}
		return null;
	}

    private void centerFrame() {
		Toolkit t = getToolkit();
		Dimension scr = t.getScreenSize ();
		setSize(width,height);
		setLocation(
			new Point(
				(scr.width - width)/2,
				(scr.height - height)/2));
    }

    private void exitApp() {
		System.exit(0);
    }

	class HeaderPanel extends JPanel {
		private JLabel oLabel = new JLabel("Ontology ID:");
		public JTextField oID = new JTextField(ontologyID, 5);
		public JCheckBox show = new JCheckBox("Show errors", showErrors);
		private JLabel tLabel = new JLabel("Max. Terms:");
		public JTextField termLimit = new JTextField(""+maxTerms, 5);
		public HeaderPanel() {
			super();
			//this.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			this.setLayout(new FlowLayout(FlowLayout.LEADING));
			this.setBackground(background);
			this.add(oLabel);
			this.add(oID);
			this.add(Box.createHorizontalStrut(10));
			show.setBackground(background);
			this.add(show);
			this.add(Box.createHorizontalStrut(10));
			this.add(tLabel);
			this.add(termLimit);
			this.add(Box.createHorizontalStrut(10));
			this.add(go);
		}
	}

	class FooterPanel extends JPanel {
		public JLabel message;
		public FooterPanel() {
			super();
			this.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			this.setLayout(new FlowLayout(FlowLayout.LEADING));
			this.setBackground(background);
			message = new JLabel("Click GO to start");
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
        	add(jsp, BorderLayout.CENTER);
		}
	}

	class Term {
		public String id = null;
		public String text = null;
		public LinkedList<String> children = new LinkedList<String>();
		public LinkedList<String> synonyms = new LinkedList<String>();

		public Term(Element result) {
			if (result.getNodeName().equals("success")) {
				Element data = getFirstNamedChild(result, "data");
				if (data != null) {
					Element classBean = getFirstNamedChild(data, "classBean");
					if (classBean != null) {
						Element idElement = getFirstNamedChild(classBean, "id");
						Element labelElement = getFirstNamedChild(classBean, "label");
						if ((idElement != null) && (labelElement != null)) {
							id = idElement.getTextContent().trim();
							text = labelElement.getTextContent().trim();
							Element synonymsElement = getFirstNamedChild(classBean, "synonyms");
							if (synonymsElement != null) {
								NodeList nl = synonymsElement.getElementsByTagName("string");
								for (int i=0; i<nl.getLength(); i++) {
									String s = ((Element)nl.item(i)).getTextContent().trim();
									if (!s.equals("")) synonyms.add(s);
								}
							}
							Element relationsElement = getFirstNamedChild(classBean, "relations");
							if (relationsElement != null) {
								NodeList nl = relationsElement.getElementsByTagName("id");
								for (int i=0; i<nl.getLength(); i++) {
									String s = ((Element)nl.item(i)).getTextContent().trim();
									if (!s.equals("")) children.add(s);
								}
							}
						}
					}
				}
			}
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(id+": "+text+"\n");
			for (String s : synonyms) {
				sb.append("   synonym: "+s+"\n");
			}
			for (String s : children) {
				sb.append("   child: "+s+"\n");
			}
			return sb.toString();
		}
	}

	//Get a child element
	private Element getFirstNamedChild(Element e, String name) {
		if (e == null) return null;
		Node child = e.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				if (child.getNodeName().equals(name)) return (Element)child;
			}
			child = child.getNextSibling();
		}
		return null;
	}

	//Make a String from an XML DOM Node.
	private static String xmlString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb,node);
		return sb.toString();
	}

	//Recursively walk the tree and write the nodes to a StringWriter.
	private static void renderNode(StringBuffer sb, Node node) {
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				NodeList nodes = node.getChildNodes();
				if (nodes != null) {
					for (int i=0; i<nodes.getLength(); i++) {
						renderNode(sb,nodes.item(i));
					}
				}
				break;

			case Node.ELEMENT_NODE:
				String name = node.getNodeName();
				NamedNodeMap attributes = node.getAttributes();
				if (attributes.getLength() == 0) {
					sb.append("<" + name + ">");
				}
				else {
					sb.append("<" + name + " ");
					int attrlen = attributes.getLength();
					for (int i=0; i<attrlen; i++) {
						Node current = attributes.item(i);
						sb.append(current.getNodeName() + "=\"" +
							escapeChars(current.getNodeValue()));
						if (i < attrlen-1)
							sb.append("\" ");
						else
							sb.append("\">");
					}
				}
				NodeList children = node.getChildNodes();
				if (children != null) {
					for (int i=0; i<children.getLength(); i++) {
						renderNode(sb,children.item(i));
					}
				}
				sb.append("</" + name + ">");
				break;

			case Node.TEXT_NODE:
			case Node.CDATA_SECTION_NODE:
				sb.append(escapeChars(node.getNodeValue()));
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sb.append("<?" + node.getNodeName() + " " +
					escapeChars(node.getNodeValue()) + "?>");
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sb.append("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;

			case Node.COMMENT_NODE:
				sb.append("<!--" + node.getNodeValue() + "-->");
				break;
		}
		return;
	}

	private static String escapeChars(String theString) {
		return theString.replace("&","&amp;")
						.replace(">","&gt;")
						.replace("<","&lt;")
						.replace("\"","&quot;")
						.replace("'","&apos;");
	}

	public static Charset latin1 = Charset.forName("ISO-8859-1");
	public static Charset utf8 = Charset.forName("UTF-8");

	// Write a string to a text file, using the specified encoding.
	private static boolean setFileText(File file, Charset charset, String text) {
		BufferedWriter bw = null;
		boolean result = true;
		try {
			bw = new BufferedWriter(
					new OutputStreamWriter(
						new FileOutputStream(file),charset));
			bw.write(text,0,text.length());
		}
		catch (Exception e) { result = false; }
		finally {
			try { bw.flush(); bw.close(); }
			catch (Exception ignore) { }
		}
		return result;
	}

	//Make a connection and return the content
	private String connect(String addr) {
		URL url;
		HttpURLConnection conn;
		int retryCount = 4;
		while (retryCount > 0) {
			try {
				url = new URL(addr);
				conn = (HttpURLConnection)url.openConnection();
				conn.setRequestMethod("GET");
				conn.connect();

				//And get the results.
				conn.getHeaderFields();
				return getContent(conn);
			}
			catch (Exception e) {
				try { Thread.sleep(1000); }
				catch (Exception ignore) { }
				retryCount--;
				if (retryCount > 0) {
					String conceptid = addr.substring(addr.indexOf("&conceptid=") + 1);
					System.out.println(termCount+": Retry connection to "+conceptid);
				}
			}
		}
		System.out.println("Unable to connect to "+addr);
		return "";
	}

	//Get the text returned in the connection.
	private String getContent(HttpURLConnection conn) {
		int length = conn.getContentLength();
		StringBuffer text = new StringBuffer();
		try {
			InputStream is = conn.getInputStream();
			InputStreamReader isr = new InputStreamReader(is, utf8);
			int size = 256;
			char[] buf = new char[size];
			int len;
			while ((len=isr.read(buf,0,size)) != -1) text.append(buf,0,len);
			isr.close();
			return text.toString();
		}
		catch (Exception e) {
			System.out.println("Unable to read response.");
			return "";
		}
	}

	class SkipNodes extends HashSet<String> {
		public SkipNodes() {
			super();
			this.add("obsolete radlex term");
			//this.add("image quality");
			//this.add("imaging service request");
			//this.add("procedure step");
			//this.add("relationship");
			//this.add("teaching attribute");
			//this.add("treatment");
		}
		public boolean skip(Term term) {
			if (term != null) {
				return this.contains(term.text.toLowerCase());
			}
			return true;
		}
	}

	class ColorPane extends JTextPane {

		public int lineHeight;
		boolean trackWidth = true;

		public ColorPane() {
			super();
			Font font = new Font("Monospaced",Font.PLAIN,12);
			FontMetrics fm = getFontMetrics(font);
			lineHeight = fm.getHeight();
			setFont(font);
			setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
			setScrollableTracksViewportWidth(false);
		}

		public boolean getScrollableTracksViewportWidth() {
			return trackWidth;
		}

		public void setScrollableTracksViewportWidth(boolean trackWidth) {
			this.trackWidth = trackWidth;
		}

		public void clear() {
			setText("");
		}

		public void appendln(String s) {
			append(s + "\n");
		}

		public void appendln(Color c, String s) {
			append(c, s + "\n");
		}

		public void append(String s) {
			int len = getDocument().getLength(); // same value as getText().length();
			setCaretPosition(len);  // place caret at the end (with no selection)
			replaceSelection(s); // there is no selection, so inserts at caret
		}

		public void append(Color c, String s) {
			StyleContext sc = StyleContext.getDefaultStyleContext();
			AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
			int len = getDocument().getLength();
			setCaretPosition(len);
			setCharacterAttributes(aset, false);
			replaceSelection(s);
		}

		public void print(Color c, String s) {
			final Color cc = c;
			final String ss = s;
			Runnable display = new Runnable() {
				public void run() {
					append(cc, ss);
				}
			};
			SwingUtilities.invokeLater(display);
		}

		public void println(Color c, String s) {
			final Color cc = c;
			final String ss = s;
			Runnable display = new Runnable() {
				public void run() {
					appendln(cc, ss);
				}
			};
			SwingUtilities.invokeLater(display);
		}

	}

}
