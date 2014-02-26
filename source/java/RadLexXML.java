package org.rsna.radlexxml;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.rsna.ui.ColorPane;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;

import org.w3c.dom.*;

/**
 * A program to walk the RadLex site, find all the terms
 * and write an XML file for use by TFS to build its RadLex
 * database. The database is used for finding terms in MIRCdocuments.
 */
public class RadLexXML extends JFrame {

	static Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

    TextPanel		textPanel;
    FooterPanel		footerPanel;
    String			windowTitle = "RadLexXML - OWL Parser - version 5";
    int 			width = 570;
    int 			height = 700;
    int				termCount = 0;
	ColorPane		text = new ColorPane();
	File			file = new File("radlex.xml");
	int 			maxTerms = 0;
	File			owlFile = null;
	Document		doc = null;;
	Element			root = null;

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
		(new ProcessingThread()).start();
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

	class ProcessingThread extends Thread {

		public ProcessingThread() { super(); }

		public void run() {
			try {
				owlFile = getOWLFile();
				footerPanel.setMessage("Filtering the OWL file...");
				File filteredFile = filter(owlFile);

				//The output doc
				footerPanel.setMessage("Creating the output document...");
				doc = XmlUtil.getDocument();
				root = doc.createElement("radlex");
				doc.appendChild(root);

				text.setText("");
				long startTime = System.currentTimeMillis();
				termCount = 0;

				//The input doc
				footerPanel.setMessage("Parsing the filtered OWL file...");
				Document owl = XmlUtil.getDocument(filteredFile);


				footerPanel.setMessage("Processing terms...");
				Element owlRoot = owl.getDocumentElement();
				Node child = owlRoot.getFirstChild();
				while (child != null) {
					if (child instanceof Element) {
						Element el = (Element)child;
						String id = el.getAttribute("ID");
						if (id.startsWith("RID")) {
							Term term = new Term(el);
							if (term.isOK()) {
								termCount++;
								term.appendTo(root);
							}
						}
					}
					child = child.getNextSibling();
				}
				FileUtil.setText(file, XmlUtil.toPrettyString(doc));
				text.print(Color.black, "\nCount = "+termCount+"\n");
				text.print(Color.red, "Elapsed time: "+(System.currentTimeMillis()-startTime) + "\n\n");
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

	private File filter(File inFile) throws Exception {
		File outFile = new File(inFile.getName() + ".xml");
		BufferedReader in = new BufferedReader(
								new InputStreamReader(
										new FileInputStream(inFile), FileUtil.utf8 ) );
		BufferedWriter out = new BufferedWriter(
								new OutputStreamWriter(
										new FileOutputStream(outFile), FileUtil.utf8 ) );
		String line;
		while ( (line=in.readLine()) != null) {
			if (!line.contains("rdf:resource=\"#RID")) {
				line = line.replaceAll("rdfs:", "");
				line = line.replaceAll("rdf:", "");
				line = line.replaceAll("datatype=\"&xsd;string\"", "");
				out.write(line);
				out.newLine();
			}
		}
		out.flush();
		out.close();
		in.close();
		return outFile;
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

	class Term {
		Element el;
		String id;
		boolean status = true;
		public LinkedList<String> names = new LinkedList<String>();
		public LinkedList<String> altnames = new LinkedList<String>();
		public LinkedList<String> synonyms = new LinkedList<String>();

		public Term(Element el) {
			this.el = el;
			id = el.getAttribute("ID");
			setStatus();
			add("Preferred_name", names);
			add("Non-English_name", altnames);
			add("Synonym", synonyms);
		}

		public boolean isOK() {
			return status && (names.size() > 0);
		}

		private void setStatus() {
			NodeList nl = el.getElementsByTagName("Term_Status");
			for (int i=0; i<nl.getLength(); i++) {
				status = !nl.item(i).getTextContent().toLowerCase().contains("retired");
			}
		}

		private void add(String tagName, LinkedList<String> list) {
			NodeList nl = el.getElementsByTagName(tagName);
			for (int i=0; i<nl.getLength(); i++) {
				String name = nl.item(i).getTextContent().trim();
				if (!name.equals("")) list.add(name);
			}
		}

		public void appendTo(Element parent) {
			for (String s : names) {
				appendTo(parent, s, "");
				//text.print(Color.black, termCount + ": " + id + ": "+ s + "\n");
			}
			for (String s : altnames) {
				appendTo(parent, s, "NEN");
				//text.print(Color.green, "              NEN: " + s + "\n");
			}
			for (String s : synonyms) {
				appendTo(parent, s, "SYN");
				//text.print(Color.blue, "              SYN: " + s + "\n");
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
