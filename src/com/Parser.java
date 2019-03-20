package com;

import java.io.IOException;

public class Parser {
    private Tokenizer t;
    private SymbolTable globalSymbolTable;
    private SymbolTable currentSymbolTable;

    /**
     * Create a new instance of the parser. This takes a stream of tokens from the
     * lexical analyzer and determines if the source code contains any grammatical errors.
     *
     * @param filePath path of the source code that needs to be parsed.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    public Parser(String filePath) throws IOException, ParserException {
        this.t = new Tokenizer(filePath);
        this.globalSymbolTable = new SymbolTable();
        this.currentSymbolTable = this.globalSymbolTable;

        while (this.t.peekNextToken().type != Token.TokenTypes.EOF)
            this.parseClass();

        System.out.println("There are no syntax errors");
        this.globalSymbolTable.printTables();
    }

    /**
     * Parse a class.
     * classDeclaration → class identifier { {memberDeclaration} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseClass() throws ParserException, IOException {
        parseKeyword("class");
        String identifier = parseIdentifier();
        this.currentSymbolTable = this.globalSymbolTable.addSymbol(identifier, "class", Symbol.KindTypes.CLASS);
        parseSymbol("{");

        while (!this.t.peekNextToken().lexeme.equals("}"))
            parseMemberDeclaration();

        parseSymbol("}");

        // restore symbol table to parent
        this.currentSymbolTable = this.currentSymbolTable.restoreParent();
    }

    /**
     * Parse a member declaration.
     * memberDeclaration → classVarDeclaration | subroutineDeclaration
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseMemberDeclaration() throws ParserException, IOException {
        Token token = this.t.peekNextToken();

        if (token.lexeme.equals("static") || token.lexeme.equals("field"))
            parseClassVarDeclaration();
        else if (token.lexeme.equals("constructor") || token.lexeme.equals("function") || token.lexeme.equals("method"))
            parseSubRoutineDeclaration();
        else
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected class member declaration. Got: " + token.lexeme);
    }

    /**
     * Parse the variable declaration section.
     * classVarDeclaration → (static | field) type identifier {, identifier} ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseClassVarDeclaration() throws ParserException, IOException {
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals("static") && !token.lexeme.equals("field"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected static or field. Got: " + token.lexeme);

        parseVariableDeclaration(false, true, token.lexeme);
        parseSymbol(";");
    }

    /**
     * Parse the type declaration for a variable.
     * type → int | char | boolean | identifier.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return String, the type that has been detected.
     */
    private String parseType() throws ParserException, IOException {
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals("int") && !token.lexeme.equals("char")
                && !token.lexeme.equals("boolean") && token.type != Token.TokenTypes.identifier)
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected type declaration. Got: " + token.lexeme);

        return token.lexeme;
    }

    /**
     * Parse the subroutine declaration section.
     * subroutineDeclaration → (constructor | function | method) (type|void) identifier (paramList) subroutineBody
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubRoutineDeclaration() throws ParserException, IOException {
        String name, type;
        boolean isConstructor = false;
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals("constructor") && !token.lexeme.equals("function") && !token.lexeme.equals("method"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected function declaration. Got: " + token.lexeme);

        if (this.t.peekNextToken().lexeme.equals("void")) {
            this.t.getNextToken();
            type = "void";
        } else
            type = parseType();

        // create function symbol table
        name = parseIdentifier();

        // check for redeclaration and add symbol
        if (this.currentSymbolTable.contains(name))
            throw new ParserException("Error, line: " + this.t.peekNextToken().lineNumber + ", Redeclaration of identifier: " + name);
        else
            this.currentSymbolTable = this.currentSymbolTable.addSymbol(name, type, Symbol.KindTypes.PROCEDURE);

        parseSymbol("(");
        parseParamList();
        parseSymbol(")");
        parseSubroutineBody();

        // restore parent symbol table
        this.currentSymbolTable = this.currentSymbolTable.restoreParent();
    }

    /**
     * Parse the parameter list.
     * paramList → type identifier {, type identifier} | ε
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseParamList() throws ParserException, IOException {
        if (this.t.peekNextToken().lexeme.equals(")"))
            return;

        parseVariableDeclaration(true, false, "argument");

        while ((this.t.peekNextToken()).lexeme.equals(",")) {
            this.t.getNextToken();
            parseVariableDeclaration(true, false,"argument");
        }
    }

    /**
     * Parse a subroutine body.
     * subroutineBody → { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineBody() throws ParserException, IOException {
        parseSymbol("{");

        while (!this.t.peekNextToken().lexeme.equals("}")) {
            parseStatement();
        }
        parseSymbol("}");
    }

    /**
     * Parse a statement.
     * statement → varDeclarStatement | letStatemnt | ifStatement | whileStatement | doStatement | returnStatemnt
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseStatement() throws ParserException, IOException {
        Token token = this.t.peekNextToken();

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
    }

    /**
     * Parse a variable declaration within a statement.
     * varDeclarStatement → var type identifier { , identifier } ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseVarDeclarStatement() throws ParserException, IOException {
        parseKeyword("var");
        parseVariableDeclaration(false, false, "var");
        parseSymbol(";");
    }

    /**
     * Parse a let statement.
     * letStatement → let identifier [ [ expression ] ] = expression ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseLetStatement() throws ParserException, IOException {
        parseKeyword("let");
        parseIdentifier(true);

        if (this.t.peekNextToken().lexeme.equals("[")) {
            this.t.getNextToken();
            parseExpression();
            parseSymbol("]");
        }
        parseSymbol("=");
        parseExpression();
        parseSymbol(";");
    }

    /**
     * Parse an if statement.
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseIfStatement() throws ParserException, IOException {
        parseKeyword("if");
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
        parseSymbol("{");
        parseStatementBody();
        parseSymbol("}");

        if (this.t.peekNextToken().lexeme.equals("else")) {
            this.t.getNextToken();
            parseSymbol("{");
            parseStatementBody();
            parseSymbol("}");
        }
    }

    /**
     * Parse a while statement.
     * whileStatement → while ( expression ) { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseWhileStatement() throws ParserException, IOException {
        parseKeyword("while");
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
        parseSymbol("{");
        parseStatementBody();
        parseSymbol("}");
    }

    /**
     * Parse a do statement.
     * doStatement → do subroutineCall ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseDoStatement() throws ParserException, IOException {
        parseKeyword("do");
        parseSubroutineCall();
        parseSymbol(";");
    }

    /** Parse a return statement.
     * returnStatemnt → return [ expression ] ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseReturnStatement() throws ParserException, IOException {
        parseKeyword("return");

        if (!this.t.peekNextToken().lexeme.equals(";"))
            parseExpression();

        parseSymbol(";");
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineCall() throws ParserException, IOException {
        parseIdentifier();
        if (this.t.peekNextToken().lexeme.equals(".")) {
            this.t.getNextToken();
            parseIdentifier();
        }

        parseSymbol("(");
        parseExpressionList();
        parseSymbol(")");
    }

    /**
     * Parse an expression list.
     * expressionList → expression { , expression } | ε
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseExpressionList() throws ParserException, IOException {
        if (!this.t.peekNextToken().lexeme.equals(")")) {
            parseExpression();

            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                parseExpression();
            }
        }
    }

    /**
     * Parse an expression.
     * expression → relationalExpression { ( & | | ) relationalExpression }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseExpression() throws ParserException, IOException {
        Token token;
        parseRelationalExpression();

        while (this.t.peekNextToken().lexeme.equals("&")
                || this.t.peekNextToken().lexeme.equals("|")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("&") && !token.lexeme.equals("|"))
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected & or |. Got: " + token.lexeme);

            parseRelationalExpression();
        }
    }

    /**
     * Parse relational expression.
     * relationalExpression → arithmeticExpression { ( = | > | < ) arithmeticExpression }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseRelationalExpression() throws ParserException, IOException {
        Token token;
        parseArithmeticExpression();

        while (this.t.peekNextToken().lexeme.equals("=")
                || this.t.peekNextToken().lexeme.equals(">")
                || this.t.peekNextToken().lexeme.equals("<")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("=") && !token.lexeme.equals("<") && !token.lexeme.equals(">"))
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected =, < or >. Got: " + token.lexeme);

            parseArithmeticExpression();
        }
    }

    /**
     * Parse an arithmetic expression.
     * arithmeticExpression → term { ( + | - ) term }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseArithmeticExpression() throws ParserException, IOException {
        Token token;
        parseTerm();

        while (this.t.peekNextToken().lexeme.equals("+")
                || this.t.peekNextToken().lexeme.equals("-")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("-") && !token.lexeme.equals("+"))
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected + or -. Got: " + token.lexeme);

            parseTerm();
        }
    }

    /**
     * Parse a term.
     * term → factor { ( * | / ) factor }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseTerm() throws ParserException, IOException {
        Token token;
        parseFactor();

        while (this.t.peekNextToken().lexeme.equals("*")
                || this.t.peekNextToken().lexeme.equals("/")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("*") && !token.lexeme.equals("/"))
                throw new ParserException("Error, line: " + token.lineNumber + ", Expected * or /. Got: " + token.lexeme);

            parseFactor();
        }
    }

    /**
     * Parse a factor.
     * factor → ( - | ~ | e ) operand
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseFactor() throws ParserException, IOException {
        if (this.t.peekNextToken().lexeme.equals("-") || this.t.peekNextToken().lexeme.equals("~"))
            this.t.getNextToken();

        parseOperand();
    }

    /**
     * Parse an operand.
     * operand → integerConstant | identifier [.identifier ] [ [ expression ] | (expressionList ) ] | (expression) | stringLiteral | true | false | null | this
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseOperand() throws ParserException, IOException {
        Token token = this.t.peekNextToken();

        // if integerConstant, stringLiteral, true, false, null, this
        if (token.type == Token.TokenTypes.integer || token.type == Token.TokenTypes.stringConstant ||
                token.lexeme.equals("true") || token.lexeme.equals("false") || token.lexeme.equals("null") ||
                token.lexeme.equals("this")) {
            this.t.getNextToken();
            return;

            // if identifier
        } if (token.type == Token.TokenTypes.identifier) {
            parseIdentifier();

            if (this.t.peekNextToken().lexeme.equals(".")) {
                this.t.getNextToken();
                parseIdentifier();
            }

            if (this.t.peekNextToken().lexeme.equals("[")) {
                this.t.getNextToken();
                parseExpression();
                parseSymbol("]");
            } else if (this.t.peekNextToken().lexeme.equals("(")) {
                this.t.getNextToken();
                parseExpressionList();
                parseSymbol(")");
            }
            return;
        }
        // if expression
        if (token.lexeme.equals("(")) {
            this.t.getNextToken();
            parseExpression();
            parseSymbol(")");
            return;
        }
        // should never be reached with 'good' source code
        throw new ParserException("Error, line: " + token.lineNumber + ", Expected beginning of operand. Got: " + token.lexeme);
    }

    /*
    ###########################################################
    Helper functions that are not strictly part of the grammar.
    ###########################################################
     */

    /**
     * Parse a symbol, given the symbol that is being looked for.
     *
     * @param symbol the symbol to be parsed.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSymbol(String symbol) throws ParserException, IOException {
        Token token = this.t.getNextToken();
        if (!token.lexeme.equals(symbol))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected " + symbol + ". Got: " + token.lexeme);
    }

    /**
     * Parse a keyword, given the keyword that is being looked for.
     *
     * @param keyword the keyword to be parsed.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseKeyword(String keyword) throws ParserException, IOException {
        Token token = this.t.getNextToken();
        if (!token.lexeme.equals(keyword))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected " + keyword + "keyword. Got: " + token.lexeme);
    }

    /**
     * Parse an Identifier.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return String, the identifier name.
     */
    private String parseIdentifier() throws ParserException, IOException {
        return parseIdentifier(false);
    }

    /**
     * Parse an Identifier.
     *
     * @param declaredCheck, check that the identifier that has been used has been declared previously.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return String, the identifier name.
     */
    private String parseIdentifier(boolean declaredCheck) throws ParserException, IOException {
        Token token = this.t.getNextToken();
        if (token.type != Token.TokenTypes.identifier)
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + token.lexeme);
        //else if (declaredCheck && this.s.getSymbol(token.lexeme) == null)
        //    throw new ParserException("Error, line: " + token.lineNumber + ", Identifier " + token.lexeme + " used without previously declaring.");

        return token.lexeme;
    }

    /**
     * Parse a variable declaration.
     * Within this method we also need to update the symbol table to be able to determine in the future
     * whether a variable has previously been declared.
     *
     * @param singleIdentifierOnly whether to continue searching for more identifiers or just detect a single identifier.
     * @param checkGlobalScope revert to checking the global scope instead of the method scope symbol table.
     * @param kind the kind of the variable i.e. static, field, argument, var.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseVariableDeclaration(boolean singleIdentifierOnly, boolean checkGlobalScope, String kind) throws ParserException, IOException {
        String type = parseType();
        String name = parseIdentifier();

        // check for redeclaration
        if (this.currentSymbolTable.contains(name))
            throw new ParserException("Error, line: " + this.t.peekNextToken().lineNumber + ", Redeclaration of identifier: " + name);

        this.currentSymbolTable.addSymbol(name, type, kind);

        if (!singleIdentifierOnly) {
            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                name = parseIdentifier();
                this.currentSymbolTable.addSymbol(name, type, kind);
            }
        }
    }

    /**
     * Parse a statement body.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseStatementBody() throws ParserException, IOException {
        if (!this.t.peekNextToken().lexeme.equals("}")) {
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                parseStatement();
            }
        }
    }
}