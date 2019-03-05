package com;

import java.io.IOException;

/*
TODO
- create custom exception for parser.
- fix infinite loop in class with no function
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
        ASTNode elmtParent = parent.addChild(newASTNode("class", ""));
        this.parent = elmtParent;

        // look for class keyword
        token = this.t.getNextToken();
        if (!token.lexeme.equals("class"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected keyword class. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "class"));

        // look for className identifier
        parseIdentifier();

        // look for '{'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("{"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '{'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "{"));

        // parse class body
        while (!this.t.peekNextToken().lexeme.equals("}")) {
            parseMemberDeclaration();
        }

        // look for '}'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '}'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "}"));

        // restore previous parent
        this.parent = elmtParent;
    }

    private void parseIdentifier() throws IOException {
        Token token = this.t.getNextToken();
        if (token.type != Token.TokenTypes.identifier)
            throw new IOException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("identifier", token.lexeme));
    }

    /**
     * Parse a member declaration.
     * memberDeclaration → classVarDeclaration | subroutineDeclaration
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseMemberDeclaration() throws IOException {
        Token token = this.t.peekNextToken();
        ASTNode elmtParent = this.parent;
        this.parent = parent.addChild(newASTNode("memberDeclaration", ""));

        if (token.lexeme.equals("static") || token.lexeme.equals("field"))
            parseClassVarDeclaration();
        else if (token.lexeme.equals("constructor") || token.lexeme.equals("function") || token.lexeme.equals("method"))
            parseSubRoutineDeclaration();

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * Parse the variable declaration section
     * classVarDeclaration → (static | field) type identifier {, identifier} ;
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseClassVarDeclaration() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("variableDeclaration", ""));
        this.parent = elmtParent;

        // look for static or field keyword
        if (!token.lexeme.equals("static") && !token.lexeme.equals("field"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected static or field. Got: " + token.lexeme);
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

        // look for instance of ';'
        token = this.t.getNextToken();
        if (!token.lexeme.equals(";"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ;. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ";"));

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * Parse the type declaration for a variable.
     * type → int | char | boolean | identifier.
     *
     * @throws IOException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseType() throws IOException {
        Token token = this.t.getNextToken();

        if (token.lexeme.equals("int") || token.lexeme.equals("char")
                || token.lexeme.equals("boolean") || token.type == Token.TokenTypes.identifier)
            parent.addChild(newASTNode("type", token.lexeme));
        else
            throw new IOException("Error, line: " + token.lineNumber + ", Expected type declaration. Got: " + token.lexeme);
    }

    /**
     * Parse the subroutine declaration section.
     * subroutineDeclaration → (constructor | function | method) (type|void) identifier (paramList) subroutineBody
     */
    private void parseSubRoutineDeclaration() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("subroutineDeclaration", ""));
        this.parent = elmtParent;

        System.out.println(token);

        // look for function keyword
        if (!token.lexeme.equals("constructor") && !token.lexeme.equals("function") && !token.lexeme.equals("method"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected function declaration. Got: " + token.lexeme);
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
        token = this.t.getNextToken();
        if (token.type != Token.TokenTypes.identifier)
            throw new IOException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("identifier", token.lexeme));

        token = this.t.getNextToken();
        if (!token.lexeme.equals("("))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '('. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "("));

        // look for parameters
        if (!this.t.peekNextToken().lexeme.equals(")"))
            parseParamList();

        // restore previous parent
        this.parent = elmtParent;

        token = this.t.getNextToken();
        if (!token.lexeme.equals(")"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ')'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ")"));

        // look for subroutine body
        parseSubroutineBody();

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * paramList → type identifier {, type identifier} | ε
     * @throws IOException
     */
    private void parseParamList() throws IOException {
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

        System.out.println(this.t.peekNextToken());
        System.out.println("closing statement");
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * subroutineBody → { {statement} }
     */
    private void parseSubroutineBody() throws IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("subroutineBody", ""));
        this.parent = elmtParent;

        // look for '{'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("{"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '{'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "{"));

        while (!this.t.peekNextToken().lexeme.equals("}")) {
            parseStatement();
        }

        // look for '}'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected '}'. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "}"));

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * statement → varDeclarStatement | letStatemnt | ifStatement | whileStatement | doStatement | returnStatemnt
     * @throws IOException
     */
    private void parseStatement() throws IOException {
        Token token = this.t.peekNextToken();
        ASTNode elmtParent = this.parent;
        this.parent = parent.addChild(newASTNode("statement", ""));

        System.out.println(token);
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
                throw new IOException("Error, line: " + token.lineNumber + ", Expected statement. Got: " + token.lexeme);
        }

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * varDeclarStatement → var type identifier { , identifier } ;
     * @throws IOException
     */
    private void parseVarDeclarStatement() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("variableDeclaration", ""));
        this.parent = elmtParent;

        if (!token.lexeme.equals("var"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected var keyword. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "var"));

        parseType();
        parseIdentifier();

        // look for no or more instances of ', identifier'
        while ((token = this.t.peekNextToken()).lexeme.equals(",")) {
            parent.addChild(newASTNode("symbol", token.lexeme));
            this.t.getNextToken();
            parseIdentifier();
        }

        // look for instance of ';'
        token = this.t.getNextToken();
        if (!token.lexeme.equals(";"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ;. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ";"));

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * letStatement → let identifier [ [ expression ] ] = expression ;
     * @throws IOException
     */
    private void parseLetStatement() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("letStatement", ""));
        this.parent = elmtParent;

        // look for instance of 'let'
        if (!token.lexeme.equals("let"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected let. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "let"));

        parseIdentifier();

        token = this.t.getNextToken();
        if (token.lexeme.equals("[")) {
            parent.addChild(newASTNode("symbol", "["));
            parseExpression();

            // look for ']'
            token = this.t.getNextToken();
            if (!token.lexeme.equals("]"))
                throw new IOException("Error, line: " + token.lineNumber + ", Expected ]. Got: " + token.lexeme);
            else
                parent.addChild(newASTNode("symbol", "]"));
            token = this.t.getNextToken();
        }

        // look for '='
        if (!token.lexeme.equals("="))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected =. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "="));

        parseExpression();

        // look for ';'
        token = this.t.getNextToken();
        if (!token.lexeme.equals(";"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ;. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ";"));

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     * @throws IOException
     */
    private void parseIfStatement() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("ifStatement", ""));
        this.parent = elmtParent;

        // look for 'if'
        if (!token.lexeme.equals("if"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected if. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "if"));

        // look for '('
        token = this.t.getNextToken();
        if (!token.lexeme.equals("("))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected (. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "("));

        parseExpression();

        // look for ')'
        token = this.t.getNextToken();
        if (!token.lexeme.equals(")"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ). Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ")"));

        // look for '{'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("{"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected {. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "{"));

        if (this.t.peekNextToken().lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected statement. Got: " + token.lexeme);
        while (!this.t.peekNextToken().lexeme.equals("}")) {
            parseStatement();
        }

        // look for '}'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected }. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "}"));

        if (this.t.peekNextToken().lexeme.equals("else")) {
            this.t.getNextToken();
            parent.addChild(newASTNode("keyword", "else"));

            // look for '{'
            token = this.t.getNextToken();
            if (!token.lexeme.equals("{"))
                throw new IOException("Error, line: " + token.lineNumber + ", Expected {. Got: " + token.lexeme);
            else
                parent.addChild(newASTNode("symbol", "{"));

            if (this.t.peekNextToken().lexeme.equals("}"))
                throw new IOException("Error, line: " + token.lineNumber + ", Expected statement. Got: " + token.lexeme);
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                parseStatement();
            }

            // look for '}'
            token = this.t.getNextToken();
            if (!token.lexeme.equals("}"))
                throw new IOException("Error, line: " + token.lineNumber + ", Expected }. Got: " + token.lexeme);
            else
                parent.addChild(newASTNode("symbol", "}"));
        }

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * whileStatement → while ( expression ) { {statement} }
     * @throws IOException
     */
    private void parseWhileStatement() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("expression", ""));
        this.parent = elmtParent;

        // look for 'while'
        if (!token.lexeme.equals("while"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected while. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "while"));

        // look for '('
        token = this.t.getNextToken();
        if (!token.lexeme.equals("("))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected (. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "("));

        parseExpression();

        // look for ')'
        token = this.t.getNextToken();
        if (!token.lexeme.equals(")"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ). Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ")"));

        // look for '{'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("{"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected {. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "{"));

        if (this.t.peekNextToken().lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected statement. Got: " + token.lexeme);
        while (!this.t.peekNextToken().lexeme.equals("}")) {
            parseStatement();
        }

        // look for '}'
        token = this.t.getNextToken();
        if (!token.lexeme.equals("}"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected }. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "}"));
    }

    /**
     * doStatement → do subroutineCall ;
     * @throws IOException
     */
    private void parseDoStatement() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("expression", ""));
        this.parent = elmtParent;

        // look for 'do'
        if (!token.lexeme.equals("do"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected do. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "do"));

        parseSubroutineCall();

        // look for ;
        token = this.t.getNextToken();
        System.out.println(token);
        if (!token.lexeme.equals(";"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ;. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ";"));

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * returnStatemnt → return [ expression ] ;
     * @throws IOException
     */
    private void parseReturnStatement() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("returnStatement", ""));
        this.parent = elmtParent;

        if (!token.lexeme.equals("return"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected return. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("keyword", "return"));

        if (!this.t.peekNextToken().lexeme.equals(";"))
            parseExpression();

        token = this.t.getNextToken();
        if (!token.lexeme.equals(";"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ;. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ";"));

        // restore previous parent
        this.parent = elmtParent;

    }

    /**
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     * @throws IOException
     */
    private void parseSubroutineCall() throws IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("subroutineCall", ""));
        this.parent = elmtParent;

        parseIdentifier();
        System.out.println(this.t.peekNextToken());
        if (this.t.peekNextToken().lexeme.equals(".")) {
            this.t.getNextToken();
            parent.addChild(newASTNode("symbol", "."));
            parseIdentifier();
        }


        token = this.t.getNextToken();
        if (!token.lexeme.equals("("))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected (. Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", "("));

        parseExpressionList();

        token = this.t.getNextToken();
        if (!token.lexeme.equals(")"))
            throw new IOException("Error, line: " + token.lineNumber + ", Expected ). Got: " + token.lexeme);
        else
            parent.addChild(newASTNode("symbol", ")"));

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * expressionList → expression { , expression } | ε
     * @throws IOException
     */
    private void parseExpressionList() throws IOException {
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
     * @throws IOException
     */
    private void parseExpression() throws IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("expression", ""));
        this.parent = elmtParent;

        parseRelationalExpression();

        while (this.t.peekNextToken().lexeme.equals("&")
                || this.t.peekNextToken().lexeme.equals("|")) {
            token = this.t.getNextToken();

            // look for & or |
            if (token.lexeme.equals("&"))
                parent.addChild(newASTNode("symbol", "&"));
            else if (token.lexeme.equals("|"))
                parent.addChild(newASTNode("symbol", "|"));
            else
                throw new IOException("Error, line: " + token.lineNumber + ", Expected & or |. Got: " + token.lexeme);

            // parse relationalExpression
            parseRelationalExpression();
        }

        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * relationalExpression → arithmeticExpression { ( = | > | < ) arithmeticExpression }
     * @throws IOException
     */
    private void parseRelationalExpression() throws IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("relationalExpression", ""));
        this.parent = elmtParent;

        parseArithmeticExpression();

        while (this.t.peekNextToken().lexeme.equals("=")
                || this.t.peekNextToken().lexeme.equals(">")
                || this.t.peekNextToken().lexeme.equals("<")) {
            token = this.t.getNextToken();

            // look for =, <, >
            if (token.lexeme.equals("="))
                parent.addChild(newASTNode("symbol", "="));
            else if (token.lexeme.equals("<"))
                parent.addChild(newASTNode("symbol", "<"));
            else if (token.lexeme.equals(">"))
                parent.addChild(newASTNode("symbol", ">"));
            else
                throw new IOException("Error, line: " + token.lineNumber + ", Expected =, < or >. Got: " + token.lexeme);

            // parse relationalExpression
            parseRelationalExpression();
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * arithmeticExpression → term { ( + | - ) term }
     * @throws IOException
     */
    private void parseArithmeticExpression() throws IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("arithmeticExpression", ""));
        this.parent = elmtParent;

        parseTerm();

        while (this.t.peekNextToken().lexeme.equals("+")
                || this.t.peekNextToken().lexeme.equals("-")) {

            token = this.t.getNextToken();

            // look for +, -
            if (token.lexeme.equals("-"))
                parent.addChild(newASTNode("symbol", "-"));
            else if (token.lexeme.equals("+"))
                parent.addChild(newASTNode("symbol", "+"));
            else
                throw new IOException("Error, line: " + token.lineNumber + ", Expected + or -. Got: " + token.lexeme);

            // parse relationalExpression
            parseTerm();
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * term → factor { ( * | / ) factor }
     * @throws IOException
     */
    private void parseTerm() throws IOException {
        Token token;
        ASTNode elmtParent = parent.addChild(newASTNode("term", ""));
        this.parent = elmtParent;

        parseFactor();

        while (this.t.peekNextToken().lexeme.equals("*")
                || this.t.peekNextToken().lexeme.equals("/")) {

            token = this.t.getNextToken();

            // look for *, /
            if (token.lexeme.equals("*"))
                parent.addChild(newASTNode("symbol", "*"));
            else if (token.lexeme.equals("/"))
                parent.addChild(newASTNode("symbol", "/"));
            else
                throw new IOException("Error, line: " + token.lineNumber + ", Expected * or /. Got: " + token.lexeme);

            // parse factor
            this.t.getNextToken();
            parseFactor();
        }
        // restore previous parent
        this.parent = elmtParent;
    }

    /**
     * factor → ( - | ~ | e ) operand
     * @throws IOException
     */
    private void parseFactor() throws IOException {
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
     * @throws IOException
     */
    private void parseOperand() throws IOException {
        Token token = this.t.getNextToken();
        ASTNode elmtParent = parent.addChild(newASTNode("operand", ""));
        this.parent = elmtParent;

        System.out.println(token);
        System.out.println(this.t.peekNextToken());

        if (token.type == Token.TokenTypes.integer)
            parent.addChild(newASTNode("integerConstant", token.lexeme));
        else if (token.type == Token.TokenTypes.identifier) {
            parent.addChild(newASTNode("identifier", token.lexeme));

            if (this.t.peekNextToken().lexeme.equals(".")) {
                this.t.getNextToken();
                parent.addChild(newASTNode("symbol", "."));
                token = this.t.getNextToken();

                if (token.type != Token.TokenTypes.identifier)
                    throw new IOException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + token.lexeme);
                else
                    parent.addChild(newASTNode("identifier", token.lexeme));
            }

            if (this.t.peekNextToken().lexeme.equals("[") || this.t.peekNextToken().lexeme.equals("(") ) {
                token = this.t.getNextToken();

                if (token.lexeme.equals("[")) {
                    parent.addChild(newASTNode("symbol", "["));
                    parseExpression();

                    this.t.getNextToken();
                    if (!token.lexeme.equals("]"))
                        throw new IOException("Error, line: " + token.lineNumber + ", Expected ']'. Got: " + token.lexeme);
                    else
                        parent.addChild(newASTNode("symbol", "]"));
                } else {
                    parent.addChild(newASTNode("symbol", "("));
                    parseExpressionList();

                    // restore previous parent
                    this.parent = elmtParent;

                    token = this.t.getNextToken();

                    if (!token.lexeme.equals(")"))
                        throw new IOException("Error, line: " + token.lineNumber + ", Expected ')'. Got: " + token.lexeme);
                    else
                        parent.addChild(newASTNode("symbol", ")"));
                }
            }
        } else if (token.lexeme.equals("(")) {
            parent.addChild(newASTNode("symbol", "("));

            parseExpression();

            if (!token.lexeme.equals(")"))
                throw new IOException("Error, line: " + token.lineNumber + ", Expected ')'. Got: " + token.lexeme);
            else
                parent.addChild(newASTNode("symbol", ")"));
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
