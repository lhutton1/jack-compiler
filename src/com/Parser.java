package com;

import java.io.IOException;

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
    }

    /**
     * Start the parser, this will take the stream of tokens and use recursive
     * decent to analyze the source code that has been input.
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     * @throws ParserException, IOException thrown if the parser comes across source code that does not match the grammar.
     */
    public void startParser() throws IOException, ParserException {
        // create a root node and assign it's element which will be used by the XML writer
        ASTNode root = newASTNode("jackProgram", "");
        root.element = x.getRootElement();
        this.parent = root;

        while (this.t.peekNextToken().type != Token.TokenTypes.EOF) {
            try {
                this.parent = root;
                this.parseClass();
            } catch (ParserException e) {
                this.x.writeXML("out/jack-parser.xml");
                throw e;
            }
        }
        this.x.writeXML("out/jack-parser.xml");
    }

    /**
     * Parse a class object.
     * classDeclaration → class identifier { {memberDeclaration} }
     *
     * @throws ParserException, IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseClass() throws ParserException, IOException {
        ASTNode elmtParent = parent.addChild(newASTNode("class", ""));
        this.parent = elmtParent;

        parseKeyword("class");
        parseIdentifier();
        parseSymbol("{");

        // parse class body
        while (!this.t.peekNextToken().lexeme.equals("}")) {
            this.parent = elmtParent; // restore previous parent
            parseMemberDeclaration();
        }

        parseSymbol("}");
        this.parent = elmtParent; // restore previous parent
    }

    /**
     * Parse a member declaration.
     * memberDeclaration → classVarDeclaration | subroutineDeclaration
     *
     * @throws ParserException, IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseMemberDeclaration() throws ParserException, IOException {
        Token token = this.t.peekNextToken();
        ASTNode elmtParent = this.parent;
        this.parent = parent.addChild(newASTNode("memberDeclaration", ""));

        if (token.lexeme.equals("static") || token.lexeme.equals("field"))
            parseClassVarDeclaration();
        else if (token.lexeme.equals("constructor") || token.lexeme.equals("function") || token.lexeme.equals("method"))
            parseSubRoutineDeclaration();
        else
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected class member declaration. Got: " + token.lexeme);

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * Parse the variable declaration section
     * classVarDeclaration → (static | field) type identifier {, identifier} ;
     *
     * @throws ParserException, IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseClassVarDeclaration() throws ParserException, IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("variableDeclaration", ""));
        this.parent = elmtParent;

        // look for static or field keyword
        if (!token.lexeme.equals("static") && !token.lexeme.equals("field"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected static or field. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", token.lexeme));

        parseType();
        parseIdentifier();

        // look for no or more instances of ', identifier'
        while ((token = this.t.peekNextToken()).lexeme.equals(",")) {
            parent.addChild(newASTNode("symbol", token.lexeme));
            this.t.getNextToken();
            parseIdentifier();
        }

        parseSymbol(";");
        this.parent = elmtParent; // restore previous parent
    }

    /**
     * Parse the type declaration for a variable.
     * type → int | char | boolean | identifier.
     *
     * @throws ParserException, IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseType() throws ParserException, IOException {
        Token token = this.t.getNextToken();

        if (token.lexeme.equals("int") || token.lexeme.equals("char")
                || token.lexeme.equals("boolean") || token.type == Token.TokenTypes.identifier)
            parent.addChild(newASTNode("type", token.lexeme));
        else
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected type declaration. Got: " + token.lexeme);
    }

    /**
     * Parse the subroutine declaration section.
     * subroutineDeclaration → (constructor | function | method) (type|void) identifier (paramList) subroutineBody
     */
    private void parseSubRoutineDeclaration() throws ParserException, IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("subroutineDeclaration", ""));
        this.parent = elmtParent;

        // look for function keyword
        if (!token.lexeme.equals("constructor") && !token.lexeme.equals("function") && !token.lexeme.equals("method"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected function declaration. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", token.lexeme));

        // look for type/void keyword
        if (this.t.peekNextToken().lexeme.equals("void")) {
            token = this.t.getNextToken();
            parent.addChild(newASTNode("keyword", token.lexeme));
        } else {
            parseType();
        }

        // look for identifier
        parseIdentifier();
        parseSymbol("(");

        // look for parameters
        if (!this.t.peekNextToken().lexeme.equals(")"))
            parseParamList();

        // restore previous parent
        this.parent = elmtParent;

        parseSymbol(")");

        // look for subroutine body
        parseSubroutineBody();

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * paramList → type identifier {, type identifier} | ε
     * @throws ParserException, IOException
     */
    private void parseParamList() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("paramList", ""));
        this.parent = elmtParent;

        parseType();
        parseIdentifier();

        while ((this.t.peekNextToken()).lexeme.equals(",")) {
            this.t.getNextToken();
            parseType();
            parseIdentifier();
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * subroutineBody → { {statement} }
     */
    private void parseSubroutineBody() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("subroutineBody", ""));
        this.parent = elmtParent;

        parseSymbol("{");

        while (!this.t.peekNextToken().lexeme.equals("}")) {
            // restore previous parent
            this.parent = elmtParent;

            parseStatement();
        }

        parseSymbol("}");

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * statement → varDeclarStatement | letStatemnt | ifStatement | whileStatement | doStatement | returnStatemnt
     * @throws ParserException, IOException
     */
    private void parseStatement() throws ParserException, IOException {
        Token token = this.t.peekNextToken();
        ASTNode elmtParent = this.parent;
        this.parent = parent.addChild(newASTNode("statement", ""));

        switch (token.lexeme) {
            case "var":
                parseVarDeclarStatement();
                break;
            case "let":
                parseLetStatement();
                break;
            case "if":
                parseIfStatement();
                break;
            case "while":
                parseWhileStatement();
                break;
            case "do":
                parseDoStatement();
                break;
            case "return":
                parseReturnStatement();
                break;
            default:
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected statement. Got: " + token.lexeme);
        }

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * varDeclarStatement → var type identifier { , identifier } ;
     * @throws ParserException, IOException
     */
    private void parseVarDeclarStatement() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("variableDeclaration", ""));
        this.parent = elmtParent;

        parseKeyword("var");

        parseType();
        parseIdentifier();

        // look for no or more instances of ', identifier'
        while ((token = this.t.peekNextToken()).lexeme.equals(",")) {
            parent.addChild(newASTNode("symbol", token.lexeme));
            this.t.getNextToken();
            parseIdentifier();
        }

        parseSymbol(";");

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * letStatement → let identifier [ [ expression ] ] = expression ;
     * @throws ParserException, IOException
     */
    private void parseLetStatement() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("letStatement", ""));
        this.parent = elmtParent;

        parseKeyword("let");
        parseIdentifier();

        if (this.t.peekNextToken().lexeme.equals("[")) {
            this.t.getNextToken();
            parent.addChild(newASTNode("symbol", "["));
            parseExpression();
            parseSymbol("]");
            token = this.t.getNextToken();
        }

        parseSymbol("=");
        parseExpression();
        parseSymbol(";");

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     * @throws ParserException, IOException
     */
    private void parseIfStatement() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("ifStatement", ""));
        this.parent = elmtParent;

        parseKeyword("if");
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
        parseSymbol("{");

        if (!this.t.peekNextToken().lexeme.equals("}")) {
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                // restore previous parent
                this.parent = elmtParent;

                parseStatement();
            }
        }

        parseSymbol("}");

        if (this.t.peekNextToken().lexeme.equals("else")) {
            this.t.getNextToken();
            parent.addChild(newASTNode("keyword", "else"));

            parseSymbol("{");

            if (this.t.peekNextToken().lexeme.equals("}")) {
                token = this.t.getNextToken();
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected statement. Got: " + token.lexeme);
            }
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                this.parent = elmtParent; // restore previous parent
                parseStatement();
            }
            parseSymbol("}");
        }
        this.parent = elmtParent; // restore previous parent
    }

    /**
     * whileStatement → while ( expression ) { {statement} }
     * @throws ParserException, IOException
     */
    private void parseWhileStatement() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("expression", ""));
        this.parent = elmtParent;

        parseKeyword("while");
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
        parseSymbol("{");

        if (!this.t.peekNextToken().lexeme.equals("}"))
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                this.parent = elmtParent; // restore previous parent
                parseStatement();
            }

        parseSymbol("}");
    }

    /**
     * doStatement → do subroutineCall ;
     * @throws ParserException, IOException
     */
    private void parseDoStatement() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("expression", ""));
        this.parent = elmtParent;

        parseKeyword("do");
        parseSubroutineCall();
        parseSymbol(";");
        this.parent = elmtParent; // restore previous parent
    }

    /**
     * returnStatemnt → return [ expression ] ;
     * @throws ParserException, IOException
     */
    private void parseReturnStatement() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("returnStatement", ""));
        this.parent = elmtParent;

        parseKeyword("return");

        if (!this.t.peekNextToken().lexeme.equals(";"))
            parseExpression();

        parseSymbol(";");
        this.parent = elmtParent; // restore previous parent
    }

    /**
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     * @throws ParserException, IOException
     */
    private void parseSubroutineCall() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("subroutineCall", ""));
        this.parent = elmtParent;

        parseIdentifier();
        if (this.t.peekNextToken().lexeme.equals(".")) {
            this.t.getNextToken();
            parent.addChild(newASTNode("symbol", "."));
            parseIdentifier();
        }

        parseSymbol("(");
        parseExpressionList();
        this.parent = elmtParent; // restore previous parent
        parseSymbol(")");
    }

    /**
     * expressionList → expression { , expression } | ε
     * @throws ParserException, IOException
     */
    private void parseExpressionList() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("expressionList", ""));
        this.parent = elmtParent;

        if (!this.t.peekNextToken().lexeme.equals(")")) {
            parseExpression();

            while (this.t.peekNextToken().lexeme.equals(",")) {
                // restore previous parent
                this.parent = elmtParent;

                parent.addChild(newASTNode("symbol", ","));
                this.t.getNextToken();
                parseExpression();
            }
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * expression → relationalExpression { ( & | | ) relationalExpression }
     * @throws ParserException, IOException
     */
    private void parseExpression() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("expression", ""));
        this.parent = elmtParent;

        parseRelationalExpression();

        while (this.t.peekNextToken().lexeme.equals("&")
                || this.t.peekNextToken().lexeme.equals("|")) {
            // restore previous parent
            this.parent = elmtParent;

            token = this.t.getNextToken();

            // look for & or |
            if (token.lexeme.equals("&"))
                parent.addChild(newASTNode("symbol", "&"));
            else if (token.lexeme.equals("|"))
                parent.addChild(newASTNode("symbol", "|"));
            else
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected & or |. Got: " + token.lexeme);

            // parse relationalExpression
            parseRelationalExpression();
        }

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * relationalExpression → arithmeticExpression { ( = | > | < ) arithmeticExpression }
     * @throws ParserException, IOException
     */
    private void parseRelationalExpression() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("relationalExpression", ""));
        this.parent = elmtParent;

        parseArithmeticExpression();

        while (this.t.peekNextToken().lexeme.equals("=")
                || this.t.peekNextToken().lexeme.equals(">")
                || this.t.peekNextToken().lexeme.equals("<")) {
            // restore previous parent
            this.parent = elmtParent;

            token = this.t.getNextToken();

            // look for =, <, >
            if (token.lexeme.equals("="))
                parent.addChild(newASTNode("symbol", "="));
            else if (token.lexeme.equals("<"))
                parent.addChild(newASTNode("symbol", "<"));
            else if (token.lexeme.equals(">"))
                parent.addChild(newASTNode("symbol", ">"));
            else
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected =, < or >. Got: " + token.lexeme);

            // parse relationalExpression
            parseArithmeticExpression();
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * arithmeticExpression → term { ( + | - ) term }
     * @throws ParserException, IOException
     */
    private void parseArithmeticExpression() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("arithmeticExpression", ""));
        this.parent = elmtParent;

        parseTerm();

        while (this.t.peekNextToken().lexeme.equals("+")
                || this.t.peekNextToken().lexeme.equals("-")) {
            // restore previous parent
            this.parent = elmtParent;

            token = this.t.getNextToken();

            // look for +, -
            if (token.lexeme.equals("-"))
                parent.addChild(newASTNode("symbol", "-"));
            else if (token.lexeme.equals("+"))
                parent.addChild(newASTNode("symbol", "+"));
            else
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected + or -. Got: " + token.lexeme);

            // parse relationalExpression
            parseTerm();

            // restore previous parent
            this.parent = elmtParent;
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * term → factor { ( * | / ) factor }
     * @throws ParserException, IOException
     */
    private void parseTerm() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("term", ""));
        this.parent = elmtParent;

        parseFactor();

        while (this.t.peekNextToken().lexeme.equals("*")
                || this.t.peekNextToken().lexeme.equals("/")) {
            // restore previous parent
            this.parent = elmtParent;

            token = this.t.getNextToken();

            // look for *, /
            if (token.lexeme.equals("*"))
                parent.addChild(newASTNode("symbol", "*"));
            else if (token.lexeme.equals("/"))
                parent.addChild(newASTNode("symbol", "/"));
            else
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected * or /. Got: " + token.lexeme);

            // parse factor
            parseFactor();
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * factor → ( - | ~ | e ) operand
     * @throws ParserException, IOException
     */
    private void parseFactor() throws ParserException, IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("factor", ""));
        this.parent = elmtParent;

        // look for -, ~
        if (this.t.peekNextToken().lexeme.equals("-")) {
            parent.addChild(newASTNode("symbol", "*"));
            this.t.getNextToken();
        } else if (this.t.peekNextToken().lexeme.equals("~")) {
            parent.addChild(newASTNode("symbol", "/"));
            this.t.getNextToken();
        }

        parseOperand();

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * operand → integerConstant | identifier [.identifier ] [ [ expression ] | (expressionList ) ] | (expression) | stringLiteral | true | false | null | this
     * @throws ParserException, IOException
     */
    private void parseOperand() throws ParserException, IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("operand", ""));
        this.parent = elmtParent;

        if (token.type == Token.TokenTypes.integer)
            parent.addChild(newASTNode("integerConstant", token.lexeme));
        else if (token.type == Token.TokenTypes.identifier) {
            parent.addChild(newASTNode("identifier", token.lexeme));

            if (this.t.peekNextToken().lexeme.equals(".")) {
                this.t.getNextToken();
                parent.addChild(newASTNode("symbol", "."));
                parseIdentifier();
            }

            if (this.t.peekNextToken().lexeme.equals("[") || this.t.peekNextToken().lexeme.equals("(") ) {
                token = this.t.getNextToken();

                if (token.lexeme.equals("[")) {
                    parent.addChild(newASTNode("symbol", "["));
                    parseExpression();
                    parseSymbol("]");
                } else {
                    parent.addChild(newASTNode("symbol", "("));
                    parseExpressionList();
                    this.parent = elmtParent; // restore previous parent
                    parseSymbol(")");
                }
            }
        } else if (token.lexeme.equals("(")) {
            parent.addChild(newASTNode("symbol", "("));
            parseExpression();
            this.parent = elmtParent; // restore previous parent
            parseSymbol(")");
        } else if (token.type == Token.TokenTypes.stringConstant)
            parent.addChild(newASTNode("stringLiteral", token.lexeme));
        else if (token.lexeme.equals("true"))
            parent.addChild(newASTNode("keyword", token.lexeme));
        else if (token.lexeme.equals("false"))
            parent.addChild(newASTNode("keyword", token.lexeme));
        else if (token.lexeme.equals("null"))
            parent.addChild(newASTNode("keyword", token.lexeme));
        else if (token.lexeme.equals("this"))
            parent.addChild(newASTNode("keyword", token.lexeme));

        // restore previous parent
        this.parent = elmtParent;
    }

    private void parseSymbol(String symbol) throws ParserException, IOException {
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals(symbol))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected " + symbol + ". Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", token.lexeme));
    }

    private void parseKeyword(String keyword) throws ParserException, IOException {
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals(keyword))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected " + keyword + "keyword. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", token.lexeme));
    }

    /**
     * Parse an Identifier.
     *
     * @throws ParserException
     * @throws IOException
     */
    private void parseIdentifier() throws ParserException, IOException {
        Token token = this.t.getNextToken();
        if (token.type != Token.TokenTypes.identifier)
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("identifier", token.lexeme));
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
