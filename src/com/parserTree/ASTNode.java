package com.parserTree;

import java.util.LinkedList;

import org.w3c.dom.*;

public class ASTNode {
    public String value;
    public String contents;
    public LinkedList<ASTNode> children;
    private XMLWriter xmlWriter;
    public Element element;

    public ASTNode(String value, String contents, XMLWriter xmlWriter) {
        this.value = value;
        this.children = new LinkedList<>();
        this.xmlWriter = xmlWriter;
        this.contents = contents;
    }

    public ASTNode addChild(ASTNode newChild) {
        // add to the XML output file
        newChild.element = this.xmlWriter.addElement(newChild.value, newChild.contents, this.element);

        this.children.add(newChild);
        return newChild;
    }

    public boolean hasChild() {
        return !this.children.isEmpty();
    }
}
