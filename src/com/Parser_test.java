package com;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import org.w3c.dom.*;

public class Parser_test {
    public static void main(String[] args) throws IOException {
        Parser parser = new Parser("in/helloworld.jack");
        parser.startParser();

//        XMLWriter test = new XMLWriter();
//
//        test.addElement("class", "hello");
//        Element t = test.addElement("test2");
//        test.addElement("child", "this is a child", t);
//        test.addElement("child2", "c2", t);
//        test.writeXML("out/jack-parser.xml");
    }
}
