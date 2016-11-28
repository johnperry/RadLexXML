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
 * A program to compare two radlex.xml files and list the differences.
 */
public class RadLexXMLCompareTool extends JFrame {

	static Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

    TextPanel textPanel;
    FooterPanel footerPanel;
    String windowTitle = "RadLexXMLCompareTool - version 1";
	ColorPane text = new ColorPane();
	File oldFile = null;
	File newFile = null;

    public static void main(String args[]) {
        new RadLexXMLCompareTool();
    }

    public RadLexXMLCompareTool() {
    	initComponents();
		oldFile = getFile("Select the old radlex.xml file");
		if (oldFile != null) {
			newFile = getFile("Select the new radlex.xml file");
			if (newFile != null) {
				(new ProcessingThread()).start();
			}
		}
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

	private File getFile(String dialogTitle) {
		File here = new File(System.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser(here);
		chooser.setDialogTitle(dialogTitle);
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
				HashSet<String> oldFileIDs = getFileIDs(oldFile);
				HashSet<String> newFileIDs = getFileIDs(newFile);
				
				HashSet<String> missingFromOldFile = getDifference(newFileIDs, oldFileIDs);
				HashSet<String> missingFromNewFile = getDifference(oldFileIDs, newFileIDs);
				
				listSet(missingFromOldFile, "RIDs missing from the old file:");
				listSet(missingFromNewFile, "RIDs missing from the new file:");
				
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
	
	private HashSet<String> getFileIDs(File file) throws Exception {
		HashSet<String> ids = new HashSet<String>();
		Document doc = XmlUtil.getDocument(file);
		Element root = doc.getDocumentElement();
		Node child = root.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				String id = ((Element)child).getAttribute("id");
				ids.add(id);
			}
			child = child.getNextSibling();
		}
		return ids;
	}
	
	private HashSet<String> getDifference(HashSet<String> a, HashSet<String> b) {
		HashSet<String> diff = new HashSet<String>(a);
		diff.removeAll(b);
		return diff;
	}
	
	private void listSet(HashSet<String> a, String title) {
		String[] ids = new String[a.size()];
		ids = a.toArray(ids);
		Arrays.sort(ids, new RIDComparator());
		text.println(Color.black, title + " ("+ids.length+")");
		for (String id : ids) {
			text.println("    " + id);
		}
		text.println("");
	}
	
	class RIDComparator implements Comparator<String> {
		public RIDComparator() { }
		public int compare(String s1, String s2) {
			if (s1.startsWith("RID") && s2.startsWith("RID")) {
				try {
					int i1 = Integer.parseInt(s1.substring(3));
					int i2 = Integer.parseInt(s2.substring(3));
					return Integer.compare(i1, i2);
				}
				catch (Exception ex) { }
			}
			return s1.compareTo(s2);
		}
	}

}
