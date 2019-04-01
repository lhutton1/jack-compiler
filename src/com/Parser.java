package com;

import java.io.IOException;
import java.util.ArrayList;

public class Parser {
    private Tokenizer t;
    private SymbolTable globalSymbolTable;
    private SymbolTable currentClassSymbolTable;
    private SymbolTable currentSymbolTable;
    private ArrayList<String> unresolvedIdentifiers;

    /**
     * Create a new instance of the parser. This takes a stream of tokens from the
     * lexical analyzer and determines if the source code contains any grammatical errors.
     *
     * @param filePath path of the source code that needs to be parsed.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    public Parser(String filePath) throws IOException, ParserException, SemanticException {
        this.t = new Tokenizer(filePath);
        this.globalSymbolTable = new SymbolTable();
        this.currentSymbolTable = this.globalSymbolTable;
        this.unresolvedIdentifiers = new ArrayList<>();

        while (this.t.peekNextToken().type != Token.TokenTypes.EOF)
            this.parseClass();

        System.out.println("There are no syntax errors");
        this.globalSymbolTable.printTables();
        System.out.println(this.unresolvedIdentifiers);
    }

    /**
     * Parse a class.
     * classDeclaration → class identifier { {memberDeclaration} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseClass() throws ParserException, SemanticException, IOException {
        parseKeyword("class");
        Token identifier = parseIdentifier();
        this.currentSymbolTable = this.globalSymbolTable.addSymbol(identifier.lexeme, identifier.lexeme, Symbol.KindTypes.CLASS, true);
        this.currentClassSymbolTable = this.currentSymbolTable;
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
    private void parseMemberDeclaration() throws ParserException, SemanticException, IOException {
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
    private void parseClassVarDeclaration() throws ParserException, SemanticException, IOException {
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals("static") && !token.lexeme.equals("field"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected static or field. Got: " + token.lexeme);

        parseVariableDeclaration(false, true, false, token.lexeme);
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
    private String parseType() throws ParserException, SemanticException, IOException {
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
    private void parseSubRoutineDeclaration() throws ParserException, SemanticException, IOException {
        String type;
        Token identifier;
        Token token = this.t.getNextToken();

        if (!token.lexeme.equals("constructor") && !token.lexeme.equals("function") && !token.lexeme.equals("method"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected function declaration. Got: " + token.lexeme);

        if (this.t.peekNextToken().lexeme.equals("void")) {
            this.t.getNextToken();
            type = "void";
        } else
            type = parseType();

        // create function symbol table
        identifier = parseIdentifier();

        // check for redeclaration and add symbol
        if (this.currentSymbolTable.contains(identifier.lexeme))
            throw new ParserException("Error, line: " + this.t.peekNextToken().lineNumber + ", Redeclaration of identifier: " + identifier.lexeme);
        else
            this.currentSymbolTable = this.currentSymbolTable.addSymbol(identifier.lexeme, type, Symbol.KindTypes.SUBROUTINE, true);

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
    private void parseParamList() throws ParserException, SemanticException, IOException {
        if (this.t.peekNextToken().lexeme.equals(")"))
            return;

        parseVariableDeclaration(true, false, true, "argument");

        while ((this.t.peekNextToken()).lexeme.equals(",")) {
            this.t.getNextToken();
            parseVariableDeclaration(true, false, true, "argument");
        }
    }

    /**
     * Parse a subroutine body.
     * subroutineBody → { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineBody() throws ParserException, SemanticException, IOException {
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
    private void parseStatement() throws ParserException, SemanticException, IOException {
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
    private void parseVarDeclarStatement() throws ParserException, SemanticException, IOException {
        parseKeyword("var");
        parseVariableDeclaration(false, false, false, "var");
        parseSymbol(";");
    }

    /**
     * Parse a let statement.
     * letStatement → let identifier [ [ expression ] ] = expression ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseLetStatement() throws ParserException, SemanticException, IOException {
        parseKeyword("let");
        Token identifier = parseIdentifier(true, false);

        if (this.t.peekNextToken().lexeme.equals("[")) {
            this.t.getNextToken();
            parseExpression();
            parseSymbol("]");
        }
        parseSymbol("=");
        parseExpression();
        parseSymbol(";");

        // the variable has now been initialized
        try {
            this.currentSymbolTable.getGlobalSymbol(identifier.lexeme).setInitialized(true);
        } catch (NullPointerException e) {
            throw new SemanticException("Error, line: " + identifier.lineNumber + ", Identifier " + identifier.lexeme + " used without previously declaring.");
        }
    }

    /**
     * Parse an if statement.
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseIfStatement() throws ParserException, SemanticException, IOException {
        this.currentSymbolTable = this.currentSymbolTable.addSymbol("", "if-stmt", Symbol.KindTypes.INNER, true);

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

        // restore symbol table to parent
        this.currentSymbolTable = this.currentSymbolTable.restoreParent();
    }

    /**
     * Parse a while statement.
     * whileStatement → while ( expression ) { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseWhileStatement() throws ParserException, SemanticException, IOException {
        this.currentSymbolTable = this.currentSymbolTable.addSymbol("", "while-stmt", Symbol.KindTypes.INNER, true);

        parseKeyword("while");
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
        parseSymbol("{");
        parseStatementBody();
        parseSymbol("}");

        // restore symbol table to parent
        this.currentSymbolTable = this.currentSymbolTable.restoreParent();
    }

    /**
     * Parse a do statement.
     * doStatement → do subroutineCall ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseDoStatement() throws ParserException, SemanticException, IOException {
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
    private void parseReturnStatement() throws ParserException, SemanticException, IOException {
        parseKeyword("return");

        if (!this.t.peekNextToken().lexeme.equals(";"))
            parseExpression();

        parseSymbol(";");

        // check for unreachable code
        Token token = this.t.peekNextToken();
        if (!token.lexeme.equals("}"))
            throw new SemanticException("Error, line: " + token.lineNumber + " Unreachable code will not be executed.");
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineCall() throws ParserException, SemanticException, IOException {
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
    private void parseExpressionList() throws ParserException, SemanticException, IOException {
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
    private void parseExpression() throws ParserException, SemanticException, IOException {
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
    private void parseRelationalExpression() throws ParserException, SemanticException, IOException {
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
    private void parseArithmeticExpression() throws ParserException, SemanticException, IOException {
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
    private void parseTerm() throws ParserException, SemanticException, IOException {
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
    private void parseFactor() throws ParserException, SemanticException, IOException {
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
    private void parseOperand() throws ParserException, SemanticException, IOException {
        Token token = this.t.peekNextToken();

        // if integerConstant, stringLiteral, true, false, null, this
        if (token.type == Token.TokenTypes.integer || token.type == Token.TokenTypes.stringConstant ||
                token.lexeme.equals("true") || token.lexeme.equals("false") || token.lexeme.equals("null") ||
                token.lexeme.equals("this")) {
            this.t.getNextToken();
            return;

            // if identifier
        } if (token.type == Token.TokenTypes.identifier) {
            Token identifier = parseIdentifier(false, false);
            Token classScopeIdentifier;
            boolean isClassIdentifier = this.t.peekNextToken().lexeme.equals(".");

            // Single identifier
            // Check to see if an identifier is declared or not
            if (!isClassIdentifier && !this.currentSymbolTable.hierarchyContains(identifier.lexeme))
                throw new SemanticException("Error, line: " + identifier.lineNumber + ", Identifier " + identifier.lexeme + " used before being declared.");
            else if (!isClassIdentifier && !this.currentSymbolTable.getGlobalSymbol(identifier.lexeme).isInitialized())
                throw new SemanticException("Error, line: " + identifier.lineNumber + ", Identifier " + identifier.lexeme + " used before being initialized.");

            // Class identifier
            if (isClassIdentifier) {
                this.t.getNextToken();
                classScopeIdentifier = parseIdentifier(false, false);

                // check if the identifier has been declared
                // if not it could be a class name.
                if (!this.currentSymbolTable.hierarchyContains(identifier.lexeme)) {
                    // potential class name
                    this.unresolvedIdentifiers.add(identifier.lexeme + "." + classScopeIdentifier.lexeme);
                } else {
                    // get the type
                    String classType = this.currentSymbolTable.getGlobalSymbol(identifier.lexeme).getType();

                    // check for class existence
                    if (this.globalSymbolTable.contains(classType)) {
                        // now we want to check the .'x' exists in that class
                        if (!this.globalSymbolTable.getSymbol(classType).getChildSymbolTable().contains(classScopeIdentifier.lexeme)) {
                            throw new SemanticException("undeclared");
                        }
                    } else {
                        this.unresolvedIdentifiers.add(classType + "." + classScopeIdentifier.lexeme);
                    }
                }
            }

//            throw new SemanticException("Error, line: " + classScopeIdentifier.lineNumber + ", Identifier " + classScopeIdentifier.lexeme
//                    + " from class variable " + identifier.lexeme + " used before being declared.");

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
    private void parseSymbol(String symbol) throws ParserException, SemanticException, IOException {
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
    private void parseKeyword(String keyword) throws ParserException, SemanticException, IOException {
        Token token = this.t.getNextToken();
        if (!token.lexeme.equals(keyword))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected " + keyword + "keyword. Got: " + token.lexeme);
    }

    /**
     * Parse an Identifier.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return Token, the identifier.
     */
    private Token parseIdentifier() throws ParserException, SemanticException, IOException {
        return parseIdentifier(false, false);
    }

    /**
     * Parse an Identifier.
     *
     * @param declaredCheck, check that the identifier that has been used has been declared previously.
     * @param initializedCheck, check whether the identifier has been initialized before being used.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return String, the identifier name.
     */
    private Token parseIdentifier(boolean declaredCheck, boolean initializedCheck) throws ParserException, SemanticException, IOException {
        Token token = this.t.getNextToken();
        String name = token.lexeme;

        // check whether the token is an identifier or 'this' keyword
        if (token.type != Token.TokenTypes.identifier && !token.lexeme.equals("this"))
            throw new ParserException("Error, line: " + token.lineNumber + ", Expected identifier. Got: " + name);

        boolean inSymbolTable = this.currentSymbolTable.hierarchyContains(name) ;

        // identifier semantic analysis
        if (declaredCheck && (!inSymbolTable))
            throw new SemanticException("Error, line: " + token.lineNumber + ", Identifier " + name + " used without previously declaring.");
        if (initializedCheck && inSymbolTable && !this.currentSymbolTable.getGlobalSymbol(name).isInitialized())
            throw new SemanticException("Error, line: " + token.lineNumber + ", Identifier " + token.lexeme + " used before being initialized.");

        return token;
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
    private void parseVariableDeclaration(boolean singleIdentifierOnly, boolean checkGlobalScope, boolean isInitialized, String kind) throws ParserException, SemanticException, IOException {
        String type = parseType();
        Token token = parseIdentifier();

        // check for redeclaration
        if (this.currentSymbolTable.hierarchyContains(token.lexeme))
            throw new SemanticException("Error, line: " + this.t.peekNextToken().lineNumber + ", Redeclaration of identifier: " + token.lexeme);

        this.currentSymbolTable.addSymbol(token.lexeme, type, kind, isInitialized);

        if (!singleIdentifierOnly) {
            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                token = parseIdentifier();
                this.currentSymbolTable.addSymbol(token.lexeme, type, kind, isInitialized);
            }
        }
    }

    /**
     * Parse a statement body.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseStatementBody() throws ParserException, SemanticException, IOException {
        if (!this.t.peekNextToken().lexeme.equals("}")) {
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                parseStatement();
            }
        }
    }
}