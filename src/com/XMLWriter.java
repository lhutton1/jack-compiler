package com;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;

import java.io.FileOutputStream;
import java.io.IOException;

public class XMLWriter {
    private Document dom;
    private DocumentBuilder db;
    private DocumentBuilderFactory dbf;
    private Element root;

    XMLWriter() {
        try {
            dbf = DocumentBuilderFactory.newInstance();
            db = dbf.newDocumentBuilder();
            dom = db.newDocument();

            // create the root now since we will need it
            root = dom.createElement("jackProgram");
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write XML document once data has been collated.
     * @param filePath the location of the .xml file
     */
    public void writeXML(String filePath) {
        this.dom.appendChild(this.root);

        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.setOutputProperty(OutputKeys.METHOD, "xml");
            tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            // send DOM to file
            tr.transform(new DOMSource(dom),
                    new StreamResult(new FileOutputStream(filePath)));

        } catch (TransformerException te) {
            System.out.println(te.getMessage());
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        }
    }

    public Element addElement(String tagName) {
        return addElement(tagName, null, this.root);
    }

    public Element addElement(String tagName, Element parent) {
        return addElement(tagName, null, parent);
    }

    public Element addElement(String tagName, String contents) {
        return addElement(tagName, contents, this.root);
    }

    public Element addElement(String tagName, String contents, Element parent) {
        Element e;

        e = dom.createElement(tagName);
        if (contents != null)
            e.appendChild(dom.createTextNode(contents));
        parent.appendChild(e);
        return e;
    }

    public Element getRootElement() {
        return this.root;
    }
}
