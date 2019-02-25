package com;

import java.io.IOException;

/*
TODO
- create custom exception for parser.
*/

public class Parser {
    private Tokenizer t;
    private XMLWriter x;
    private ASTNode parent;

    /**
     * Create a new instance of the parser. This takes a stream of tokens from the
     * lexical analyzer and generates an abstract syntax tree.
     *
     * @param filePath path of the source code that needs to be parsed.
     */
    Parser (String filePath) {
        this.t = new Tokenizer(filePath);
        this.x = new XMLWriter();

        // create a root node and assign it's element which will be used by the XML writer
        ASTNode root = newASTNode("jack-program", "t");
        root.element = x.getRootElement();
        this.parent = root;
    }

    /**
     * Start the parser, this will take the stream of tokens and use recursive
     * decent to analyze the source code that has been input.
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     */
    public void startParser() throws IOException {
        //while (this.t.peekNextToken().type != Token.TokenTypes.EOF) {
        try {
            this.parseClass();
            this.x.writeXML("out/jack-parser.xml");
        } catch (IOException e) {
            this.x.writeXML("out/jack-parser.xml");
            throw e;
        }
        //}
    }

    /**
     * Parse a class object.
     * classDeclaration → class identifier { {memberDeclaration} }
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseClass() throws IOException {
        Token token;
        ASTNode classParent = parent.addChild(newASTNode("class", ""));
        this.parent = classParent;


        // look for class keyword
        token = this.t.getNextToken();
        if (!token.lexeme.equals("class"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected keyword class. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "class"));

        // look for className identifier
        token = this.t.getNextToken();
        if(token.type != Token.TokenTypes.identifier)
            throw new IOException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("identifier", token.lexeme));

        // look for '{'
        token = this.t.getNextToken();
        if(!token.lexeme.equals("{"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '{'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "{"));

        // parse class body
        parseMemberDeclaration();

        // look for '}'
        token = this.t.getNextToken();
        if(!token.lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '}'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "}"));

        // restore previous parent
        this.parent = classParent;
    }

    /**
     * Parse a member declaration.
     * memberDeclaration → classVarDeclaration | subroutineDeclaration
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseMemberDeclaration() throws IOException {
        Token token = this.t.peekNextToken();
        parent = parent.addChild(new ASTNode("memberDeclaration", "", x));

        if(token.lexeme.equals("static") || token.lexeme.equals("field"))
            parseClassVarDeclaration();
        else if (token.lexeme.equals("constructor") || token.lexeme.equals("function") || token.lexeme.equals("method"))
            parseSubRoutineDeclaration();
    }

    /**
     * Parse the variable declaration section
     * classVarDeclaration → (static | field) type identifier {, identifier} ;
     */
    private void parseClassVarDeclaration() {
        System.out.println("reached");
    }

    /**
     * Parse the subroutine declaration section.
     * subroutineDeclaration → (constructor | function | method) (type|void) identifier (paramList) subroutineBody
     */
    private void parseSubRoutineDeclaration() {
        System.out.println("reached2");
    }

    /**
     * Helper function to add new node to the syntax tree.
     * @param value the value that token represents.
     * @param content the content of the token.
     * @return a new node.
     */
    private ASTNode newASTNode(String value, String content) {
        return new ASTNode(value, content, this.x);
    }
}
