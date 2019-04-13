package com;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class CompilationEngine {
    private Tokenizer t;
    private VMWriter w;
    private SymbolTable globalSymbolTable;
    //private SymbolTable currentClassSymbolTable;
    private SymbolTable currentSubroutineTable;
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
    public CompilationEngine(String filePath) throws IOException, ParserException, SemanticException {
        this.t = new Tokenizer(filePath);
        this.globalSymbolTable = new SymbolTable();
        this.currentSymbolTable = this.globalSymbolTable;
        this.unresolvedIdentifiers = new ArrayList<>();

        //while (this.t.peekNextToken().type != Token.TokenTypes.EOF)
            this.parseClass();

        System.out.println("There are no syntax errors");
        this.globalSymbolTable.printTables();
        System.out.println(this.unresolvedIdentifiers);
    }

    public CompilationEngine(File file) throws IOException, ParserException, SemanticException {
        this.t = new Tokenizer(file);
        this.w = new VMWriter(file);
        this.unresolvedIdentifiers = new ArrayList<>();
    }

    public void run() throws IOException, ParserException, SemanticException {
        this.parseClass();
        this.currentSymbolTable.printTables();

        try {
            this.w.close();
        } catch (IOException e) {
            System.err.println("The output file could not be closed");
            System.exit(1);
        }
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
        Token identifier = parseIdentifier(false, false);

        // Semantic analysis - check for redefinition
        //if (this.globalSymbolTable.contains(identifier.lexeme))
         //   throw new SemanticException(identifier.lineNumber, "Identifier " + identifier.lexeme + " has already been declared in this scope.");

        Symbol rootSymbol = new Symbol(identifier.lexeme, identifier.lexeme, Symbol.Kind.CLASS, 0, true);
        this.globalSymbolTable = new SymbolTable(null, rootSymbol);
        this.currentSymbolTable = this.globalSymbolTable;


        parseSymbol("{");

        while (!this.t.peekNextToken().lexeme.equals("}"))
            parseMemberDeclaration(identifier.lexeme);

        parseSymbol("}");
    }

    /**
     * Parse a member declaration.
     * memberDeclaration → classVarDeclaration | subroutineDeclaration
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseMemberDeclaration(String className) throws ParserException, SemanticException, IOException {
        Token token = this.t.peekNextToken();
        if (token.lexeme.equals("static") || token.lexeme.equals("field"))
            parseClassVarDeclaration();
        else if (token.lexeme.equals("constructor") || token.lexeme.equals("function") || token.lexeme.equals("method"))
            parseSubRoutineDeclaration(className);
        else
            throw new ParserException(token.lineNumber, "Expected class member declaration. Got: " + token.lexeme);
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
            throw new ParserException(token.lineNumber, "Expected static or field. Got: " + token.lexeme);

        parseVariableDeclaration(false, false, token.lexeme);
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
            throw new ParserException(token.lineNumber, "Expected type declaration. Got: " + token.lexeme);

        return token.lexeme;
    }

    /**
     * Parse the subroutine declaration section.
     * subroutineDeclaration → (constructor | function | method) (type|void) identifier (paramList) subroutineBody
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubRoutineDeclaration(String className) throws ParserException, SemanticException, IOException {
        String type;
        Token identifier;
        Token token = this.t.getNextToken();
        LinkedList<Symbol> symbols;

        if (!token.lexeme.equals("constructor") && !token.lexeme.equals("function") && !token.lexeme.equals("method"))
            throw new ParserException(token.lineNumber, "Expected function declaration. Got: " + token.lexeme);

        if (this.t.peekNextToken().lexeme.equals("void")) {
            this.t.getNextToken();
            type = "void";
        } else
            type = parseType();

        // create function symbol table
        identifier = parseIdentifier();

        // check for redeclaration and add symbol
        if (this.currentSymbolTable.contains(identifier.lexeme))
            throw new ParserException(this.t.peekNextToken().lineNumber, "Redeclaration of identifier: " + identifier.lexeme);
        else {
            this.currentSymbolTable = this.currentSymbolTable.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(token.lexeme), true).getSymbolTable();
            this.currentSubroutineTable = this.currentSymbolTable;
        }

        parseSymbol("(");
        symbols = parseParamList();
        parseSymbol(")");

        boolean returnsAllCodePaths = parseSubroutineBody();

        // VM CODE - generate the function
        this.w.writeLine("function " + this.globalSymbolTable.getName() + "." + identifier.lexeme + " " + this.currentSymbolTable.getLocalCount());

        if (token.lexeme.equals("constructor")) {
            this.w.writeLine("push constant " + this.globalSymbolTable.getFieldCount());
            this.w.writeLine("call Memory.alloc 1");
            this.w.writeLine("pop pointer 0");
        } else if (token.lexeme.equals("method")) {
            this.w.writeLine("push argument 0");
            this.w.writeLine("pop pointer 0");
        }

        this.w.writeNow();

        if (!type.equals("void") && !returnsAllCodePaths)
            throw new SemanticException(this.t.peekNextToken().lineNumber, "Not all code paths return.");

        // restore parent symbol table
        this.currentSymbolTable = this.currentSymbolTable.getParent();
    }

    /**
     * Parse the parameter list.
     * paramList → type identifier {, type identifier} | ε
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private LinkedList<Symbol> parseParamList() throws ParserException, SemanticException, IOException {
        LinkedList<Symbol> symbols = new LinkedList<>();

        if (this.t.peekNextToken().lexeme.equals(")"))
            return null;

        symbols.add(parseVariableDeclaration(true, true, "argument"));

        while ((this.t.peekNextToken()).lexeme.equals(",")) {
            this.t.getNextToken();
            symbols.add(parseVariableDeclaration(true, true, "argument"));
        }

        return symbols;
    }

    /**
     * Parse a subroutine body.
     * subroutineBody → { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseSubroutineBody() throws ParserException, SemanticException, IOException {
        boolean returnsOnAllCodePaths = false;
        parseSymbol("{");

        while (!this.t.peekNextToken().lexeme.equals("}")) {
            if (parseStatement())
                returnsOnAllCodePaths = true;
        }

        parseSymbol("}");
        return returnsOnAllCodePaths;
    }

    /**
     * Parse a statement.
     * statement → varDeclarStatement | letStatemnt | ifStatement | whileStatement | doStatement | returnStatemnt
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseStatement() throws ParserException, SemanticException, IOException {
        boolean returnsOnAllCodePaths = false;
        Token token = this.t.peekNextToken();

        switch (token.lexeme) {
            case "var":
                parseVarDeclarStatement();
                break;
            case "let":
                parseLetStatement();
                break;
            case "if":
                returnsOnAllCodePaths = parseIfStatement();
                break;
            case "while":
                parseWhileStatement();
                break;
            case "do":
                parseDoStatement();
                break;
            case "return":
                parseReturnStatement();
                returnsOnAllCodePaths = true;
                break;
            default:
                throw new ParserException(token.lineNumber + "Expected statement. Got: " + token.lexeme);
        }

        return returnsOnAllCodePaths;
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
        parseVariableDeclaration(false, false, "local");
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
        String returnType;
        parseKeyword("let");
        Token identifier = parseIdentifier(true, false);
        boolean isArrayIdentifier = false;

        if (this.t.peekNextToken().lexeme.equals("[")) {
            isArrayIdentifier = true;
            this.t.getNextToken();
            returnType = parseExpression();

            if (!returnType.equals("int"))
                throw new SemanticException(identifier.lineNumber, "Expression in array indices must always evaluate to an integer.");

            parseSymbol("]");
        }


        parseSymbol("=");

        returnType = parseExpression();
        if (!returnType.equals("") && !returnType.equals("Array") && !returnType.equals(this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).getType()))
            throw new SemanticException(identifier.lineNumber, "Cannot assign type " + returnType + " to "
                    + identifier.lexeme + ".");

        parseSymbol(";");

        if (!isArrayIdentifier) {
            // VM CODE - pop variable
            this.w.writeLater("pop " + this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).getKind().toString() +
                    " " + this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).getIndex());
        }

        // the variable has now been initialized
        try {
            this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).setInitialized(true);
        } catch (NullPointerException e) {
            throw new SemanticException(identifier.lineNumber, "Identifier " + identifier.lexeme + " used without previously declaring.");
        }
    }

    /**
     * Parse an if statement.
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseIfStatement() throws ParserException, SemanticException, IOException {
        boolean returnsOnAllCodePaths = false;
        boolean elseReturnsOnAllCodePaths = false;
        this.currentSymbolTable = this.currentSymbolTable.addSymbol("", "if-stmt", Symbol.Kind.INNER, true).getSymbolTable();

        parseKeyword("if");
        parseConditionalStatment();
        parseSymbol("{");
        returnsOnAllCodePaths = parseStatementBody();
        parseSymbol("}");

        if (this.t.peekNextToken().lexeme.equals("else")) {
            this.t.getNextToken();
            parseSymbol("{");
            elseReturnsOnAllCodePaths = parseStatementBody();
            parseSymbol("}");
        }

        // restore symbol table to parent
        this.currentSymbolTable = this.currentSymbolTable.getParent();
        return returnsOnAllCodePaths && elseReturnsOnAllCodePaths;
    }

    /**
     * Parse a while statement.
     * whileStatement → while ( expression ) { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseWhileStatement() throws ParserException, SemanticException, IOException {
        this.currentSymbolTable = this.currentSymbolTable.addSymbol("", "while-stmt", Symbol.Kind.INNER, true).getSymbolTable();

        parseKeyword("while");
        parseConditionalStatment();
        parseSymbol("{");
        parseStatementBody();
        parseSymbol("}");

        // restore symbol table to parent
        this.currentSymbolTable = this.currentSymbolTable.getParent();
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

        // VM CODE - pop temp
        this.w.writeLater("pop temp 0");

        parseSymbol(";");
    }

    /** Parse a return statement.
     * returnStatemnt → return [ expression ] ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseReturnStatement() throws ParserException, SemanticException, IOException {
        String type = "";
        parseKeyword("return");

        if (!this.t.peekNextToken().lexeme.equals(";"))
            type = parseExpression();

        // SEMANTIC ANALYSIS - check return type matches
        type = type.equals("") ? "void" : type;
        if (this.currentSubroutineTable != null && !this.currentSubroutineTable.getInfo().getType().equals(type))
            throw new SemanticException(this.t.peekNextToken().lineNumber, "Return type " + type + " not compatible with return type specified in function declaration.");

        // VM CODE - push void
        System.out.println(type);
        if (type.equals("void"))
            this.w.writeLater("push constant 0");
        this.w.writeLater("return");

        parseSymbol(";");

        // check for unreachable code
        Token token = this.t.peekNextToken();
        if (!token.lexeme.equals("}"))
            throw new SemanticException(token.lineNumber, "Unreachable code will not be executed.");
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineCall() throws ParserException, SemanticException, IOException {
        Map<String, String> results = parseClassScopeIdentifier();
        //String type = results.get("type");

        parseSymbol("(");
        ArrayList<String> paramList = parseExpressionList();
        checkSubroutineArguments(results.get("classIdentifier"), results.get("classMemberIdentifier"), paramList);
        parseSymbol(")");

        // VM CODE - call identifier
        this.w.writeLater("call " +  + ".");
    }

    /**
     * Parse an expression list.
     * expressionList → expression { , expression } | ε
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private ArrayList<String> parseExpressionList() throws ParserException, SemanticException, IOException {
        ArrayList<String> paramTypes = new ArrayList<>();

        if (!this.t.peekNextToken().lexeme.equals(")")) {
            paramTypes.add(parseExpression());

            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                paramTypes.add(parseExpression());
            }
        }

        return paramTypes;
    }

    /**
     * Parse an expression.
     * expression → relationalExpression { ( & | | ) relationalExpression }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseExpression() throws ParserException, SemanticException, IOException {
        String type;
        Token token;
        type = parseRelationalExpression();

        while (this.t.peekNextToken().lexeme.equals("&")
                || this.t.peekNextToken().lexeme.equals("|")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("&") && !token.lexeme.equals("|"))
                throw new ParserException(token.lineNumber, "Expected & or |. Got: " + token.lexeme);

            type = parseRelationalExpression();
        }
        return type;
    }

    /**
     * Parse relational expression.
     * relationalExpression → arithmeticExpression { ( = | > | < ) arithmeticExpression }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseRelationalExpression() throws ParserException, SemanticException, IOException {
        String type;
        Token token;
        type = parseArithmeticExpression();

        while (this.t.peekNextToken().lexeme.equals("=")
                || this.t.peekNextToken().lexeme.equals(">")
                || this.t.peekNextToken().lexeme.equals("<")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("=") && !token.lexeme.equals("<") && !token.lexeme.equals(">"))
                throw new ParserException(token.lineNumber, "Expected =, < or >. Got: " + token.lexeme);

            type = parseArithmeticExpression();
        }

        return type;
    }

    /**
     * Parse an arithmetic expression.
     * arithmeticExpression → term { ( + | - ) term }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseArithmeticExpression() throws ParserException, SemanticException, IOException {
        String type;
        Token token;
        type = parseTerm();

        while (this.t.peekNextToken().lexeme.equals("+")
                || this.t.peekNextToken().lexeme.equals("-")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("-") && !token.lexeme.equals("+"))
                throw new ParserException(token.lineNumber + "Expected + or -. Got: " + token.lexeme);

            type = parseTerm();
        }

        return type;
    }

    /**
     * Parse a term.
     * term → factor { ( * | / ) factor }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseTerm() throws ParserException, SemanticException, IOException {
        String type;
        Token token;
        type = parseFactor();



        while (this.t.peekNextToken().lexeme.equals("*")
                || this.t.peekNextToken().lexeme.equals("/")) {
            token = this.t.getNextToken();

            if (!token.lexeme.equals("*") && !token.lexeme.equals("/"))
                throw new ParserException(token.lineNumber + "Expected * or /. Got: " + token.lexeme);

            type = parseFactor();
        }
        return type;
    }

    /**
     * Parse a factor.
     * factor → ( - | ~ | e ) operand
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseFactor() throws ParserException, SemanticException, IOException {
        if (this.t.peekNextToken().lexeme.equals("-") || this.t.peekNextToken().lexeme.equals("~"))
            this.t.getNextToken();
        return parseOperand();
    }

    /**
     * Parse an operand.
     * operand → integerConstant | identifier [.identifier ] [ [ expression ] | (expressionList ) ] | (expression) | stringLiteral | true | false | null | this
     * (edited to allow this keyword to represent a class variable)
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseOperand() throws ParserException, SemanticException, IOException {
        String type = "";
        Token token = this.t.peekNextToken();

        // if integerConstant, stringLiteral, true, false, null, this
        if (token.type == Token.TokenTypes.integer || token.type == Token.TokenTypes.stringConstant ||
                token.lexeme.equals("true") || token.lexeme.equals("false") || token.lexeme.equals("null")) {
            this.t.getNextToken();

            // establish type
            if (token.type == Token.TokenTypes.integer) {
                this.w.writeLater("push constant " + token.lexeme);
                type = "int";
            } else if (token.type == Token.TokenTypes.stringConstant) {
                this.w.writeLater("push constant " + token.lexeme.length());
                this.w.writeLater("call String.new 1");
                type = "stringConstant";
            } else if (token.lexeme.equals("true") || token.lexeme.equals("false")) {
                if (token.lexeme.equals("true")) {
                    this.w.writeLater("push constant 1");
                    this.w.writeLater("neg");
                } else
                    this.w.writeLater("push constant 0");

                type = "boolean";
            } else {
                this.w.writeLater("push constant 0");
                type = "null";
            }

            return type;

        // if identifier
        } if (token.type == Token.TokenTypes.identifier || token.lexeme.equals("this")) {
            Map<String, String> results = parseClassScopeIdentifier();
            type = results.get("type");

            if (!this.t.peekNextToken().lexeme.equals("."))
                this.w.writeLater("push pointer 0");

            if (this.t.peekNextToken().lexeme.equals("[")) {
                String returnType;
                Token arrayIndexStart = this.t.getNextToken();
                returnType = parseExpression();

                if (!returnType.equals("int"))
                    throw new SemanticException(arrayIndexStart.lineNumber, "Expression in array indices must always evaluate to an integer.");

                parseSymbol("]");
                type = "Array";
            } else if (this.t.peekNextToken().lexeme.equals("(")) {
                this.t.getNextToken();
                ArrayList<String> paramTypes = parseExpressionList();
                checkSubroutineArguments(results.get("classIdentifier"), results.get("classMemberIdentifier"), paramTypes);
                parseSymbol(")");
            }
            return type;
        }

        // if expression
        if (token.lexeme.equals("(")) {
            this.t.getNextToken();
            parseExpression();
            parseSymbol(")");
            return type;
        }
        // should never be reached with 'good' source code
        throw new ParserException(token.lineNumber, "Expected beginning of operand. Got: " + token.lexeme);
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
            throw new ParserException(token.lineNumber, "Expected " + symbol + ". Got: " + token.lexeme);
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
            throw new ParserException(token.lineNumber, "Expected " + keyword + "keyword. Got: " + token.lexeme);
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
        boolean isClassScopeVariable = false;
        boolean inSymbolTable = false;

        // check whether the token is an identifier or 'this' keyword
        if (token.lexeme.equals("this")) {
            if (this.t.peekNextToken().lexeme.equals(".")) {
                isClassScopeVariable = true;
                token = this.t.getNextToken();
            }

            name = token.lexeme;

        } else if (token.type != Token.TokenTypes.identifier)
            throw new ParserException(token.lineNumber, "Expected identifier. Got: " + name);

        if (declaredCheck || initializedCheck) {
            if (isClassScopeVariable)
                inSymbolTable = this.globalSymbolTable.hierarchyContains(name);
            else
                inSymbolTable = this.currentSymbolTable.hierarchyContains(name);


            // identifier semantic analysis
            if (declaredCheck && (!inSymbolTable) && isClassScopeVariable)
                throw new SemanticException(token.lineNumber, "Identifier this." + name + " used without previously declaring.");
            else if (declaredCheck && (!inSymbolTable))
                throw new SemanticException(token.lineNumber, "Identifier " + name + " used without previously declaring.");

            if (initializedCheck && inSymbolTable && !this.currentSymbolTable.findHierarchySymbol(name).isInitialized() && isClassScopeVariable)
                throw new SemanticException(token.lineNumber, "Identifier this." + name + " used before being initialized.");
            else if (initializedCheck && inSymbolTable && !this.currentSymbolTable.findHierarchySymbol(name).isInitialized())
                throw new SemanticException(token.lineNumber, "Identifier " + name + " used before being initialized.");
        }


        return token;
    }

    /**
     * Parse a variable declaration.
     * Within this method we also need to update the symbol table to be able to determine in the future
     * whether a variable has previously been declared.
     *
     * @param singleIdentifierOnly whether to continue searching for more identifiers or just detect a single identifier.
     * @param kind the kind of the variable i.e. static, field, argument, var.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private Symbol parseVariableDeclaration(boolean singleIdentifierOnly, boolean isInitialized, String kind) throws ParserException, SemanticException, IOException {
        String type = parseType();
        Token token = parseIdentifier();
        Symbol symbol;

        // check for redeclaration
        if (this.currentSymbolTable.subroutineContains(token.lexeme))
            throw new SemanticException(this.t.peekNextToken().lineNumber, "Redeclaration of identifier: " + token.lexeme);

        symbol = this.currentSymbolTable.addSymbol(token.lexeme, type, Symbol.Kind.fromString(kind), isInitialized).getSymbol();

        if (!singleIdentifierOnly) {
            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                token = parseIdentifier();
                this.currentSymbolTable.addSymbol(token.lexeme, type, Symbol.Kind.valueOf(kind), isInitialized);
            }
        }
        return symbol;
    }

    /**
     * Parse a statement body.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseStatementBody() throws ParserException, SemanticException, IOException {
        boolean returnsOnAllCodePaths = false;

        if (!this.t.peekNextToken().lexeme.equals("}")) {
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                returnsOnAllCodePaths = parseStatement();
            }
        }

        return returnsOnAllCodePaths;
    }

    private Map<String, String> parseClassScopeIdentifier() throws ParserException, SemanticException, IOException {
        String type = "";
        Token identifier = parseIdentifier(false, false);
        Token classScopeIdentifier = null;
        boolean isClassIdentifier = this.t.peekNextToken().lexeme.equals(".");

        // Single identifier
        // Check to see if an identifier is declared or not
        if (!isClassIdentifier) {
            if (identifier.lexeme.equals("this")) {
                type = this.globalSymbolTable.getInfo().getType();
            } else if (!this.currentSymbolTable.hierarchyContains(identifier.lexeme))
                throw new SemanticException(identifier.lineNumber, "Identifier " + identifier.lexeme + " used before being declared.");
            else if (!this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).isInitialized())
                throw new SemanticException(identifier.lineNumber, "Identifier " + identifier.lexeme + " used before being initialized.");
            else
                type = this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).getType();
        }

        // Class identifier
        if (isClassIdentifier) {
            this.t.getNextToken();
            classScopeIdentifier = parseIdentifier(false, false);

            // check if the class level identifier has been declared
            if (!this.currentSymbolTable.hierarchyContains(identifier.lexeme)) {
                this.unresolvedIdentifiers.add(identifier.lexeme + "." + classScopeIdentifier.lexeme);
            } else {
                String classType = this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).getType();

                if (this.globalSymbolTable.contains(classType)) {
                    // now we want to check the .'x' exists in that class
                    if (!this.globalSymbolTable.findSymbol(classType).getChildSymbolTable().contains(classScopeIdentifier.lexeme)) {
                        throw new SemanticException(classScopeIdentifier.lineNumber, "Identifier " + classScopeIdentifier.lexeme
                                + " from class variable " + identifier.lexeme + " used before being declared.");
                    }
                } else {
                    this.unresolvedIdentifiers.add(classType + "." + classScopeIdentifier.lexeme);
                }
            }

            // in some cases the type cannot be retrieved since the type is not yet known e.g. class declared in another file.
            try {
                type = this.globalSymbolTable.findSymbol(identifier.lexeme).getChildSymbolTable().findSymbol(classScopeIdentifier.lexeme).getType();
            } catch (NullPointerException e) {
                type = "";
            }
        }

        HashMap<String, String> map = new HashMap<>();
        map.put("classIdentifier", identifier.lexeme);
        map.put("type", type);

        if (classScopeIdentifier != null)
            map.put("classMemberIdentifier", classScopeIdentifier.lexeme);
        else
            map.put("classMemberIdentifier", null);

        return map;
    }

    private void checkSubroutineArguments(String identifier, String classMemberIdentifier, ArrayList<String> paramTypes) throws SemanticException, IOException {
        SymbolTable subroutine;
        HashMap<Integer, String> subroutineSymbolTypes;

        boolean isClassScopeIdentifier = classMemberIdentifier != null;

        // if the subroutine has not yet been found then don't check argument types.
        try {
            subroutine = !isClassScopeIdentifier ? this.currentSymbolTable.findHierarchySymbol(identifier).getChildSymbolTable() :
                    this.globalSymbolTable.findSymbol(identifier).getChildSymbolTable().findSymbol(classMemberIdentifier).getChildSymbolTable();
            subroutineSymbolTypes = subroutine.getArgumentSymbols();
        } catch(NullPointerException e) {
            return;
        }

        // check that the number of arguments
        if (paramTypes.size() != subroutine.getArgumentCount() - 1)
            throw new SemanticException(this.t.peekNextToken().lineNumber, "The number of arguments for the function call doesn't match that of the declaration.");

        // check that arguments and no.arguments match.
        for (int x = 0; x < paramTypes.size(); x++) {
            if (!paramTypes.get(x).equals(subroutineSymbolTypes.get(x+1)))
                throw new SemanticException(this.t.peekNextToken().lineNumber, "The type: " + paramTypes.get(x) + " doesn't match the type used in the function declaration.");
        }
    }

    private void parseConditionalStatment() throws IOException, ParserException, SemanticException{
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
    }
}