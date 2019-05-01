package com;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * The compilation engine parses, semantically analyses and writes the
 * vm code of a .jack source file.
 */
public class CompilationEngine {
    private final boolean DEBUGGING = false;                 // If true print the symbol table and unresolved identifiers at the end of compilation.
    private final boolean SEMANTIC_ANALYSIS = true;         // If true perform the semantic analysis checks on the source code.

    private final Tokenizer t;                              // Tokenizer object that reads from a source file.
    private final VMWriter w;                               // Object that writes vm code to file.
    private final File f;                                   // The file we are currently writing to.

    private SymbolTable globalSt;                           // The symbol table for the class.
    private SymbolTable subSt;                              // The symbol table of the current subroutine.
    private SymbolTable cSt;                                // The symbol table of the current scope.

    private LinkedList<Identifier> unresolvedIdentifiers;    // Identifiers that couldn't be resolved and need to be checked at the end.
    private int labelCounter;                               // Counter used to generate a unique label id for if and while statements.
    private boolean semanticStatus;                         // The current status of the semantic checks. If an error occurs this equals false.

    // Colour the text on the command line appropriately
    // Source: https://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println
    private static final String ANSI_RED = "\033[0;91m";
    private static final String ANSI_YELLOW = "\033[0;93m";
    private static final String ANSI_RESET = "\u001B[0m";

    /**
     * Get the status of the semantics of the source code.
     * @return True = no semantic error occurred, False = at least one semantic error occurred
     */
    public boolean getSemanticStatus() { return this.semanticStatus; }

    /**
     * Initialize the compilation engine.
     * @param file the file that is to be compiled
     * @throws IOException thrown if file cannot be opened, or read from.
     */
    public CompilationEngine(File file) throws IOException {
        this.f = file;
        this.t = new Tokenizer(file);
        this.w = new VMWriter(file);
        this.unresolvedIdentifiers = new LinkedList<>();
        this.semanticStatus = true;
    }

    /**
     * Run the compilation engine.
     * Start by parsing a class, if everything is ok compilation is complete.
     * @throws IOException thrown if the .jack source file cannot be read.
     * @throws ParserException thrown if the parser runs into an issue and must stop.
     */
    public void run() throws IOException, ParserException {
        // Parse a single class
        this.parseClass();

        // SEMANTIC ANALYSIS - Check to make sure that only one class has been created
        if (this.t.peekNextToken().type != Token.Types.EOF) {
            semanticError(this.t.peekNextToken().lineNumber, "Expected end of file, only one class per file.");
        }

        // Resolve unresolved identifiers
        resolveIdentifiers();

        // Only run if no semantic errors have been output
        if (DEBUGGING) {
            this.globalSt.printTables();
            System.out.println("Unresolved identifiers: " + this.unresolvedIdentifiers + " <to be solved by semantic analysis>");
        }

        // Compilation successful
        if (semanticStatus)
            System.out.println("[Compilation successful] " + this.f.getName());
        else
            this.w.deleteFile();

        // Close the writer
        try {
            this.w.close();
        } catch (IOException e) {
            System.err.println("The output file could not be closed.");
            System.exit(1);
        }
    }

    /**
     * Handles outputting a semantic error to the terminal.
     * @param lineNumber the line number the error occurred at.
     * @param msg the message that will be output, providing information to the user.
     */
    private void semanticError(int lineNumber, String msg) {
        if (SEMANTIC_ANALYSIS) {
            System.err.println(ANSI_RED + "[Semantic error] Line " + lineNumber + ": " + msg + ANSI_RESET);
            semanticStatus = false;
        }
    }

    /**
     * Outputs a semantic warning.
     * Note: This will not stop the VMWriter from outputting the vm code.
     * @param lineNumber the line number the error occurred at.
     * @param msg the message that will be output, providing the information to the user.
     */
    private void semanticWarning(int lineNumber, String msg) {
        if (SEMANTIC_ANALYSIS)
            System.err.println(ANSI_YELLOW + "[Semantic warning] Line " + lineNumber + ": " + msg + ANSI_RESET);
    }

    /**
     * Resolve identifier that are yet to be resolved after the compilation has finished.
     * Note: this happens when a function is used before it has been declared or if a function
     * is part of another class.
     * @throws IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void resolveIdentifiers() throws IOException {
        for(Iterator<Identifier> iter = this.unresolvedIdentifiers.iterator(); iter.hasNext(); ) {
            Identifier id = iter.next();

            if (id.getCIdName().equals(this.globalSt.getName())) {
                if (!this.globalSt.contains(id.getIdName())) {
                    semanticError(id.getLineNumber(), "Identifier " + id.getIdName() + " used without previously declaring.");
                } else {
                    id.setId(this.globalSt.findSymbol(id.getIdName()));
                    checkSubroutineArguments(id, id.getArguments());
                }

                iter.remove();
            }
        }
    }

    /*
    ###########################################################
                       Parse the grammar
    ###########################################################
     */

    /**
     * Parse a class.
     * classDeclaration → class identifier { {memberDeclaration} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseClass() throws ParserException, IOException {
        parseKeyword("class");

        // Symbol table is yet to be initialized so instead of calling
        // parseIdentifier() parse the symbol here
        Token identifier = this.t.getNextToken();
        if (identifier.type != Token.Types.IDENTIFIER)
            throw new ParserException(identifier.lineNumber, "Expected identifier got: " + identifier.lexeme);

        // After the class name has been retrieved we can set up the symbol table
        Symbol rootSymbol = new Symbol(identifier.lexeme, identifier.lexeme, Symbol.Kind.CLASS, 0, true);
        this.globalSt = new SymbolTable(null, rootSymbol);
        this.cSt = this.globalSt;

        parseSymbol("{");

        // Parse member declarations until exhausted
        while (!this.t.peekNextToken().lexeme.equals("}"))
            parseMemberDeclaration();

        parseSymbol("}");
    }

    /**
     * Parse a member declaration.
     * memberDeclaration → classVarDeclaration | subroutineDeclaration
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseMemberDeclaration() throws ParserException, IOException {
        Token decType = this.t.peekNextToken();

        if (decType.lexeme.equals("static") || decType.lexeme.equals("field"))
            parseClassVarDeclaration();
        else if (decType.lexeme.equals("constructor") || decType.lexeme.equals("function") || decType.lexeme.equals("method"))
            parseSubRoutineDeclaration();
        else
            throw new ParserException(decType.lineNumber, "Expected class member declaration. Got: " + decType.lexeme);
    }

    /**
     * Parse the variable declaration section.
     * classVarDeclaration → (static | field) type identifier {, identifier} ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseClassVarDeclaration() throws ParserException, IOException {
        Token decType = this.t.getNextToken();

        if (!decType.lexeme.equals("static") && !decType.lexeme.equals("field"))
            throw new ParserException(decType.lineNumber, "Expected static or field. Got: " + decType.lexeme);

        String kind  = decType.lexeme.equals("field") ? "this" : decType.lexeme;
        parseVariableDeclaration(false, false, kind);
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
        Token type = this.t.getNextToken();

        if (!type.lexeme.equals("int") && !type.lexeme.equals("char")
                && !type.lexeme.equals("boolean") && type.type != Token.Types.IDENTIFIER)
            throw new ParserException(type.lineNumber, "Expected type declaration. Got: " + type.lexeme);

        return type.lexeme;
    }

    /**
     * Parse the subroutine declaration section.
     * subroutineDeclaration → (constructor | function | method) (type|void) identifier (paramList) subroutineBody
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubRoutineDeclaration() throws ParserException, IOException {
        String type;
        Token identifier;
        String functionType = this.t.getNextToken().lexeme;

        if (!functionType.equals("constructor") && !functionType.equals("function") && !functionType.equals("method"))
            throw new ParserException(this.t.peekNextToken().lineNumber, "Expected function declaration. Got: " + functionType);

        // Resolve the type of the function
        if (this.t.peekNextToken().lexeme.equals("void")) {
            this.t.getNextToken();
            type = "void";
        } else
            type = parseType();

        // Now resolve the identifier
        if (this.t.peekNextToken().type != Token.Types.IDENTIFIER)
            throw new ParserException(this.t.peekNextToken().lineNumber, "Expected identifier. Got: " + this.t.peekNextToken().lexeme);
        identifier = this.t.getNextToken();

        // Check for redeclaration and add symbol
        // Note: for some reason jack seems to allow class variables
        // and subroutines with the same names a work around has
        // been included for this situation.
        if (this.cSt.contains(identifier.lexeme)) {
            boolean isClassVariable = this.cSt.scopeFindSymbol(identifier.lexeme).getKind() == Symbol.Kind.FIELD
                    || this.cSt.scopeFindSymbol(identifier.lexeme).getKind() == Symbol.Kind.STATIC;

            if (!isClassVariable)
                throw new ParserException(identifier.lineNumber, "Redeclaration of identifier, " + identifier.lexeme);
        } else {
            ChildSymbol newFunctionSymbol = this.cSt.addSymbol(
                    identifier.lexeme,
                    type,
                    Symbol.Kind.fromString(functionType),
                    true
            );

            this.cSt = newFunctionSymbol.getSymbolTable();
            this.subSt = this.cSt;
        }

        parseSymbol("(");
        parseParamList();
        parseSymbol(")");
        parseSymbol("{");
        boolean returnsAllCodePaths = parseSubroutineBody();

        // VM CODE - generate the function
        this.w.writeLine("function " + this.globalSt.getName() + "." + identifier.lexeme + " " + this.cSt.getLocalCount());

        if (functionType.equals("constructor")) {
            // We need to allocate memory for the object and obtain a
            // pointer for 'this'.
            this.w.writeLine("push constant " + this.globalSt.getFieldCount());
            this.w.writeLine("call Memory.alloc 1");
            this.w.writeLine("pop pointer 0");
        } else if (functionType.equals("method")) {
            this.w.writeLine("push argument 0");
            this.w.writeLine("pop pointer 0");
        }

        // Write any pending code -
        // This is a work around to allow the number of local variables to
        // be found for a function declaration. Since the VM code function
        // declaration looks like:
        // 'function Class.funcName #localVars'
        this.w.writeNow();

        // SEMANTIC ANALYSIS - check all code paths return.
        if (!type.equals("void") && !returnsAllCodePaths)
            semanticError(this.t.peekNextToken().lineNumber, "Not all code paths return.");

        parseSymbol("}");

        // Restore parent symbol table
        this.cSt = this.cSt.getParent();
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

        parseVariableDeclaration(true, true, "argument");

        while ((this.t.peekNextToken()).lexeme.equals(",")) {
            this.t.getNextToken();
            parseVariableDeclaration(true, true, "argument");
        }
    }

    /**
     * Parse a subroutine body.
     * subroutineBody → { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseSubroutineBody() throws ParserException, IOException {
        boolean returnsOnAllCodePaths = false;

        while (!this.t.peekNextToken().lexeme.equals("}")) {
            boolean statementReturns = parseStatement();

            if (statementReturns)
                returnsOnAllCodePaths = true;
        }

        return returnsOnAllCodePaths;
    }

    /**
     * Parse a statement.
     * statement → varDeclarationStatement | letStatement | ifStatement | whileStatement | doStatement | returnStatement
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseStatement() throws ParserException, IOException {
        boolean returnsOnAllCodePaths = false;
        Token statementStart = this.t.peekNextToken();

        switch (statementStart.lexeme) {
            case "var":
                parseVarDeclarationStatement();
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
                throw new ParserException(statementStart.lineNumber, "Expected statement. Got: " + statementStart.lexeme);
        }

        return returnsOnAllCodePaths;
    }

    /**
     * Parse a variable declaration within a statement.
     * varDeclarationStatement → var type identifier { , identifier } ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseVarDeclarationStatement() throws ParserException, IOException {
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
    private void parseLetStatement() throws ParserException, IOException {
        String type;
        Identifier identifier;
        boolean isArrayIdentifier = false;

        parseKeyword("let");
        identifier = parseIdentifier(true, false);

        // Check if the identifier has an array index after it
        if (this.t.peekNextToken().lexeme.equals("[")) {
            isArrayIdentifier = true;
            this.t.getNextToken();

            type = parseExpression();

            // VM CODE - Add array index to vm code
            if (identifier != null) {
                this.w.writeLater("push " + identifier.getKind().toString() + " " + identifier.getIndex());
                this.w.writeLater("add");
            }

            // SEMANTIC ANALYSIS - Check that an array index evaluates to an integer constant
            if (identifier != null && !type.equals("int"))
                semanticError(identifier.getLineNumber(), "Expression in array indices must always evaluate to an integer.");

            parseSymbol("]");
        }

        parseSymbol("=");
        type = parseExpression();

        // SEMANTIC ANALYSIS - Check type matches LHS.
        // Things to note:
        // - Array can support any data type.
        // - Int can be indirectly converted to a boolean.
        // TODO Clean up.
        if (identifier != null && identifier.getType().equals("boolean") && type.equals("int")) {
            type = "boolean";
        } if (identifier != null && !type.equals("") && !type.equals(identifier.getType()) && !identifier.getType().equals("Array") &&
                (type.equals("boolean") || type.equals("char") || type.equals("int")))
            semanticError(identifier.getLineNumber(), "Cannot assign type " + type + " to " + identifier.getType() + ".");

        parseSymbol(";");

        // VM CODE - Update the identifier value
        if (identifier != null && !isArrayIdentifier) {
            this.w.writeLater("pop " + identifier.getKind().toString() + " " + identifier.getIndex());
        } else {
            this.w.writeLater("pop temp 0");
            this.w.writeLater("pop pointer 1");
            this.w.writeLater("push temp 0");
            this.w.writeLater("pop that 0");
        }

        // Initialize the variable
        if (identifier != null && identifier.getId() != null)
            identifier.setInitialized(true);

        // SEMANTIC ANALYSIS - Identifier has not been declared in this scope
        else if (identifier != null)
            semanticError(identifier.getLineNumber(), "Identifier " + identifier.getIdName() + " used without previously declaring.");
    }

    /**
     * Parse an if statement.
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseIfStatement() throws ParserException, IOException {
        boolean returnsOnAllCodePaths;
        boolean elseReturnsOnAllCodePaths = true;

        // Increment label counter to generate unique label value
        int labelValue = this.labelCounter++;

        // Add the if statement to the symbol table
        ChildSymbol childSymbol = this.cSt.addSymbol(
            "",
            "if-stmt",
            Symbol.Kind.INNER,
            true
        );
        this.cSt = childSymbol.getSymbolTable();

        parseKeyword("if");
        parseConditionalStatement();

        // VM CODE - Write if statement vm code
        this.w.writeLater("if-goto IF_TRUE" + labelValue);
        this.w.writeLater("goto IF_FALSE" + labelValue);
        this.w.writeLater("label IF_TRUE" + labelValue);

        parseSymbol("{");
        returnsOnAllCodePaths = parseStatementBody();
        parseSymbol("}");

        // VM CODE - write if statement code
        if (this.t.peekNextToken().lexeme.equals("else"))
            this.w.writeLater("goto IF_END" + labelValue);
        this.w.writeLater("label IF_FALSE" + labelValue);

        // Optional else statement
        if (this.t.peekNextToken().lexeme.equals("else")) {
            this.t.getNextToken();
            parseSymbol("{");
            elseReturnsOnAllCodePaths = parseStatementBody();
            parseSymbol("}");

            // VM CODE - write if statement code
            this.w.writeLater("label IF_END" + labelValue);
        }

        // Restore symbol table to parent
        this.cSt = this.cSt.getParent();

        return returnsOnAllCodePaths && elseReturnsOnAllCodePaths;
    }

    /**
     * Parse a while statement.
     * whileStatement → while ( expression ) { {statement} }
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseWhileStatement() throws ParserException, IOException {
        // Increment label counter to generate unique label value
        int labelValue = this.labelCounter++;

        // Add the while statement to the symbol table
        ChildSymbol childSymbol = this.cSt.addSymbol(
                "",
                "while-stmt",
                Symbol.Kind.INNER,
                true
        );
        this.cSt = childSymbol.getSymbolTable();

        parseKeyword("while");

        // VM CODE - Write while statement to vm
        this.w.writeLater("label WHILE_EXP" + labelValue);

        parseConditionalStatement();

        // VM CODE - Add the conditional go to statement
        this.w.writeLater("not");
        this.w.writeLater("if-goto WHILE_END" + labelValue);

        parseSymbol("{");
        parseStatementBody();
        parseSymbol("}");

        // VM CODE - Write end of the while statement
        this.w.writeLater("goto WHILE_EXP" + labelValue);
        this.w.writeLater("label WHILE_END" + labelValue);

        // Restore symbol table to parent
        this.cSt = this.cSt.getParent();
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

        // VM CODE - Pop temp value
        this.w.writeLater("pop temp 0");

        parseSymbol(";");
    }

    /** Parse a return statement.
     * returnStatement → return [ expression ] ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseReturnStatement() throws ParserException, IOException {
        String type = "void";
        parseKeyword("return");

        if (!this.t.peekNextToken().lexeme.equals(";"))
            type = parseExpression();

        // SEMANTIC ANALYSIS - Check return type matches function declaration
        if (this.subSt != null) {
            String functionType = this.subSt.getSymbol().getType();

            if (this.subSt.getSymbol().getKind() == Symbol.Kind.CONSTRUCTOR && !this.globalSt.getName().equals(type)) {
                semanticError(this.t.peekNextToken().lineNumber, "A constructor must return 'this'.");
            } else if (!functionType.equals(type) && !functionType.equals("int")) {
                semanticError(this.t.peekNextToken().lineNumber, "Return type " + type + " not compatible with return type " +
                        this.subSt.getSymbol().getType() + " specified in function declaration.");
            }
        }


        // VM CODE - Push void and return
        if (this.subSt != null && this.subSt.getType().equals("void"))
            this.w.writeLater("push constant 0");
        this.w.writeLater("return");

        parseSymbol(";");

        // SEMANTIC ANALYSIS - Check for unreachable code
        Token unreachable = this.t.peekNextToken();
        if (!unreachable.lexeme.equals("}"))
            semanticWarning(unreachable.lineNumber, "Unreachable code will not be executed.");
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineCall() throws ParserException, IOException {
        parseSubroutineCall(parseIdentifier(false, true));
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    public void parseSubroutineCall(Identifier identifier) throws ParserException, IOException {
        int offset = 0;

        parseSymbol("(");

        // VM CODE - Write code to call a subroutine
        // if method invocation in the same class
        if (identifier != null && identifier.getCIdName() != null &&
                (identifier.getCIdName().equals(this.globalSt.getName()) || identifier.getCIdName().equals(""))) {
            if (this.subSt.getKind() == Symbol.Kind.METHOD || this.subSt.getKind() == Symbol.Kind.CONSTRUCTOR) {
                if (this.cSt.scopeContains(identifier.getCIdName())) {
                    this.w.writeLater("push " + identifier.getKind().toString() + " "
                            + identifier.getIndex());
                    offset = 1;
                } else if (identifier.getCIdName().equals("this")) {
                    this.w.writeLater("push pointer 0");
                    offset = 1;
                }
            } else {
                // SEMANTIC ANALYSIS - Check if method invocation from function
                if (identifier.getId() != null
                        && (identifier.getId().getKind() == Symbol.Kind.METHOD))
                    semanticError(identifier.getLineNumber(), "Function cannot call a method.");
            }

        // If method invocation is in another class
        } else {
            // Check to see if the first identifier needs to be pushed
            if (identifier != null && identifier.getCId() != null && this.cSt.scopeContains(identifier.getCIdName())) {
                this.w.writeLater("push " + identifier.getCId().getKind().toString() + " " + identifier.getCId().getIndex());
                offset = 1;
            }

            // Check if the second identifier exists and push it
            if (identifier != null && identifier.getKind() != null) {
                this.w.writeLater("push " + identifier.getKind().toString() + " " + identifier.getIndex());
            }
        }

        // SEMANTIC ANALYSIS - Check that the subroutine arguments match.
        LinkedList<String> paramList = parseExpressionList();
        checkSubroutineArguments(identifier, paramList);

        // VM CODE - Call identifier
        if (identifier != null)
            this.w.writeLater("call " + identifier.toString() + " " + (paramList.size() + offset));

        parseSymbol(")");
    }

    /**
     * Parse an expression list.
     * expressionList → expression { , expression } | ε
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private LinkedList<String> parseExpressionList() throws ParserException, IOException {
        LinkedList<String> paramTypes = new LinkedList<>();

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
    private String parseExpression() throws ParserException, IOException {
        String type;
        Token operator;

        type = parseRelationalExpression();

        while (this.t.peekNextToken().lexeme.equals("&")
                || this.t.peekNextToken().lexeme.equals("|")) {
            operator = this.t.getNextToken();

            if (!operator.lexeme.equals("&") && !operator.lexeme.equals("|"))
                throw new ParserException(operator.lineNumber, "Expected & or |. Got: " + operator.lexeme);

            type = parseRelationalExpression();

            // VM CODE - Write & / | to vm
            if (operator.lexeme.equals("&"))
                this.w.writeLater("and");
            else
                this.w.writeLater("or");
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
    private String parseRelationalExpression() throws ParserException, IOException {
        String type;
        Token operator;
        type = parseArithmeticExpression();

        while (this.t.peekNextToken().lexeme.equals("=")
                || this.t.peekNextToken().lexeme.equals(">")
                || this.t.peekNextToken().lexeme.equals("<")) {
            operator = this.t.getNextToken();

            if (!operator.lexeme.equals("=") && !operator.lexeme.equals("<") && !operator.lexeme.equals(">"))
                throw new ParserException(operator.lineNumber, "Expected =, < or >. Got: " + operator.lexeme);

            type = parseArithmeticExpression();

            // VM CODE - write = / < / > to vm
            if (operator.lexeme.equals("="))
                this.w.writeLater("eq");
            else if (operator.lexeme.equals("<"))
                this.w.writeLater("lt");
            else
                this.w.writeLater("gt");
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
    private String parseArithmeticExpression() throws ParserException, IOException {
        String type;
        Token operator;
        type = parseTerm();

        while (this.t.peekNextToken().lexeme.equals("+")
                || this.t.peekNextToken().lexeme.equals("-")) {
            operator = this.t.getNextToken();

            if (!operator.lexeme.equals("-") && !operator.lexeme.equals("+"))
                throw new ParserException(operator.lineNumber + "Expected + or -. Got: " + operator.lexeme);

            type = parseTerm();

            // VM CODE - add or subtract using vm
            if (operator.lexeme.equals("+"))
                this.w.writeLater("add");
            else
                this.w.writeLater("sub");
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
    private String parseTerm() throws ParserException, IOException {
        String type;
        Token operator;
        type = parseFactor();

        while (this.t.peekNextToken().lexeme.equals("*")
                || this.t.peekNextToken().lexeme.equals("/")) {
            operator = this.t.getNextToken();

            if (!operator.lexeme.equals("*") && !operator.lexeme.equals("/"))
                throw new ParserException(operator.lineNumber + "Expected * or /. Got: " + operator.lexeme);

            type = parseFactor();

            // VM CODE - Write multiply / divide function calls
            if (operator.lexeme.equals("*"))
                this.w.writeLater("call Math.multiply 2");
            else
                this.w.writeLater("call Math.divide 2");
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
    private String parseFactor() throws ParserException, IOException {
        String type;
        Token operator = this.t.peekNextToken();

        if (operator.lexeme.equals("-") || operator.lexeme.equals("~"))
            this.t.getNextToken();

        type =  parseOperand();

        // VM CODE - Write negate / not to vm
        if (operator.lexeme.equals("-"))
            this.w.writeLater("neg");
        else if (operator.lexeme.equals("~"))
            this.w.writeLater("not");

        return type;
    }

    /**
     * Parse an operand.
     * operand → integerConstant | identifier [.identifier ] [ [ expression ] | (expressionList) ] | (expression) | stringLiteral | true | false | null | this | subroutineCall
     * (edited to allow this keyword to represent a class variable)
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseOperand() throws ParserException, IOException {
        String type;
        Token typeToken = this.t.peekNextToken();

        // If the token is of a constant type
        if (typeToken.type == Token.Types.INTEGER || typeToken.type == Token.Types.STRING_CONSTANT ||
                typeToken.lexeme.equals("true") || typeToken.lexeme.equals("false") || typeToken.lexeme.equals("null")) {
            this.t.getNextToken();

            // Establish the type of the token
            if (typeToken.type == Token.Types.INTEGER) {
                // VM CODE - Write integer constant
                this.w.writeLater("push constant " + typeToken.lexeme);

                type = "int";
            } else if (typeToken.type == Token.Types.STRING_CONSTANT) {
                // VM CODE - Write string constant
                this.w.writeLater("push constant " + typeToken.lexeme.length());
                this.w.writeLater("call String.new 1");

                for (char letter : typeToken.lexeme.toCharArray()) {
                    this.w.writeLater("push constant " + (int)letter);
                    this.w.writeLater("call String.appendChar 2");
                }

                type = "string_constant";
            } else if (typeToken.lexeme.equals("true") || typeToken.lexeme.equals("false")) {
                // VM CODE - Write true/false constants
                if (typeToken.lexeme.equals("true")) {
                    this.w.writeLater("push constant 0");
                    this.w.writeLater("not");
                } else
                    this.w.writeLater("push constant 0");

                type = "boolean";
            } else {
                // VM CODE - Push a constant of 0 for null keyword
                this.w.writeLater("push constant 0");

                type = "null";
            }

            return type;

        // If the token is an identifier
        } if (typeToken.type == Token.Types.IDENTIFIER || typeToken.lexeme.equals("this")) {
            Identifier identifier = parseIdentifier(false, true);
            type = identifier != null ? identifier.getType() : "";

            // VM CODE - If the token type is not part of an array or subroutine call
            if (!this.t.peekNextToken().lexeme.equals("[") && !this.t.peekNextToken().lexeme.equals("(")) {
                if (identifier != null) {
                    // Try to resolve the symbol based on it already being in the symbol table
                    if ((identifier.getCIdName().equals("") || identifier.getCIdName().equals(this.globalSt.getName()))
                            && this.cSt.scopeContains(identifier.getIdName())) {
                        this.w.writeLater("push " + identifier.getKind() + " " + identifier.getIndex());

                        // TODO check to see if this is actually needed, can it go ^^?
                        // Special case for constructor due to 'return this' being possible in constructor
                    } else if (typeToken.lexeme.equals("this") && !this.t.peekNextToken().lexeme.equals(".")
                            && this.subSt.getKind() == Symbol.Kind.CONSTRUCTOR)
                        this.w.writeLater("push pointer 0");
                }
            }

            // If the token is part of an array index
            else if (this.t.peekNextToken().lexeme.equals("[")) {
                Token arrayIndexStart = this.t.getNextToken();
                String arrayIndexType = parseExpression();

                // VM CODE - Write the identifier
                if (identifier != null) {
                    this.w.writeLater("push " + identifier.getKind().toString() + " " + identifier.getIndex());
                    this.w.writeLater("add");
                }

                if (!arrayIndexType.equals("int"))
                    semanticError(arrayIndexStart.lineNumber,
                            "Expression in array indices must always evaluate to an integer. Type received was, " + arrayIndexType);

                parseSymbol("]");

                // VM CODE - Handle the array index
                this.w.writeLater("pop pointer 1");
                this.w.writeLater("push that 0");
                type = arrayIndexType;

            // If the token is part of a subroutine call
            } else if (this.t.peekNextToken().lexeme.equals("("))
                parseSubroutineCall(identifier);

            return type;
        }

        // If expression is part of operand
        if (typeToken.lexeme.equals("(")) {
            this.t.getNextToken();
            type = parseExpression();
            parseSymbol(")");
            return type;
        }

        // Should never be reached with 'good' source code
        throw new ParserException(typeToken.lineNumber, "Expected beginning of operand. Got: " + typeToken.lexeme);
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
            throw new ParserException(token.lineNumber, "Expected " + symbol + ". Got: " + token.lexeme);
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
            throw new ParserException(token.lineNumber, "Expected " + keyword + " keyword. Got: " + token.lexeme);
    }

    /**
     * Parse an Identifier.
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return Token, the identifier.
     */
    private Identifier parseIdentifier() throws ParserException, IOException {
        return parseIdentifier(false, false);
    }

    /**
     * Parse an Identifier, this is a hard task due to the many forms an identifier can come in.
     *
     * @param declaredCheck, check that the identifier that has been used has been declared previously.
     * @param initializedCheck, check whether the identifier has been initialized before being used.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @return String, the identifier name.
     */
    private Identifier parseIdentifier(boolean declaredCheck, boolean initializedCheck) throws ParserException, IOException {
        Token newToken = this.t.getNextToken();
        boolean isClassIdentifier;

        // If the token is not an identifier we can immediately throw an error
        if (newToken.type != Token.Types.IDENTIFIER && !newToken.lexeme.equals("this"))
            throw new ParserException(newToken.lineNumber, "Expected identifier. Got: " + newToken.lexeme);

        // We need to decide whether the identifier has a class scope associated with it.
        // If it doesn't, we process it as an identifier that must exist in the current class.
        // If it does, we process it with more caution because it could be from an external class.
        isClassIdentifier = this.t.peekNextToken().lexeme.equals(".");

        // Single identifier
        if (!isClassIdentifier) {
            // Cover the case that the identifier may not have been declared and must be resolved later, after compilation
            if (!this.cSt.scopeContains(newToken.lexeme) && !newToken.lexeme.equals("this") ) {
                // SEMANTIC ANALYSIS - Identifier used without declaring
                if (declaredCheck) {
                    semanticError(newToken.lineNumber, "Identifier " + newToken.lexeme + " used without previously declaring.");
                    // If we don't want to check whether the identifier is declared, add it to the unresolved identifiers
                } else {
                    Identifier unresolvedIdentifier = new Identifier(
                            this.globalSt.getName(),
                            getClassObject(),
                            newToken.lexeme,
                            null,
                            newToken.lineNumber
                    );
                    this.unresolvedIdentifiers.add(unresolvedIdentifier);
                    return unresolvedIdentifier;
                }

            // At this point we know the identifier is in the symbol table so return it
            } else {
                // SEMANTIC ANALYSIS - Check to see if the symbol has been initialized
                if (initializedCheck && !this.cSt.scopeFindSymbol(newToken.lexeme).isInitialized())
                    semanticWarning(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

                return new Identifier(
                        this.globalSt.getName(),
                        getClassObject(),
                        newToken.lexeme,
                        this.cSt.scopeFindSymbol(newToken.lexeme),
                        newToken.lineNumber
                );
            }
        }

        // Class identifier
        else {
            this.t.getNextToken(); // Skip the '.'
            Token newScopedToken = this.t.getNextToken();

            // Check to see if the class level identifier has been declared (it could be a variable or the name of a class)
            if (!this.cSt.scopeContains(newToken.lexeme) && !newToken.lexeme.equals(this.globalSt.getName())) {
                // SEMANTIC ANALYSIS - Check to see if the class level symbol has been initialized
                if (initializedCheck && this.cSt.scopeContains(newToken.lexeme) && !this.cSt.scopeFindSymbol(newToken.lexeme).isInitialized())
                    semanticWarning(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

                Identifier unresolvedIdentifier = new Identifier(
                        newToken.lexeme,
                        newScopedToken.lexeme,
                        newToken.lineNumber
                );
                this.unresolvedIdentifiers.add(unresolvedIdentifier);
                return unresolvedIdentifier;
            }

            // We now know that the outer identifier has been defined so we can proceed to check the inner identifier
            // This case covers when the class identifier is of the current class
            else if (newToken.lexeme.equals(this.globalSt.getName())) {
                if (!this.globalSt.contains(newScopedToken.lexeme)) {
                    // SEMANTIC ANALYSIS - Identifier used without declaring
                    if (declaredCheck)
                        semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                    else {
                        Identifier unresolvedIdentifier = new Identifier(
                                this.globalSt.getName(),
                                newScopedToken.lexeme,
                                newScopedToken.lineNumber
                        );
                        this.unresolvedIdentifiers.add(unresolvedIdentifier);
                        return unresolvedIdentifier;
                    }
                } else {
                    return new Identifier(
                            newToken.lexeme,
                            this.cSt.scopeFindSymbol(newToken.lexeme),
                            newScopedToken.lexeme,
                            this.globalSt.findSymbol(newScopedToken.lexeme),
                            newScopedToken.lineNumber
                    );
                }

            // Covers the case when the identifier is a variable identifier referencing a class
            } else {
                // SEMANTIC ANALYSIS - Check that the identifier has been initialized since it is now known to be a variable
                if (initializedCheck && !this.cSt.scopeFindSymbol(newToken.lexeme).isInitialized())
                    semanticWarning(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

                // Check the inner identifier
                String classType = this.cSt.scopeFindSymbol(newToken.lexeme).getType();

                // If the inner identifier is part of another class
                if (!this.globalSt.getName().equals(classType)) {
                    // SEMANTIC ANALYSIS - Identifier used without declaring
                    if (declaredCheck)
                        semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                    else {
                        Identifier unresolvedIdentifier = new Identifier(
                                this.cSt.scopeFindSymbol(newToken.lexeme).getType(),
                                this.cSt.scopeFindSymbol(newToken.lexeme),
                                newScopedToken.lexeme,
                                null,
                                newToken.lineNumber
                        );
                        this.unresolvedIdentifiers.add(unresolvedIdentifier);
                        return unresolvedIdentifier;
                    }
                // Otherwise, it is referencing the current class
                } else {
                    if (!this.globalSt.contains(newScopedToken.lexeme)) {
                        // SEMANTIC ANALYSIS - Check that the identifier has been initialized since it is now known to be a variable
                        if (initializedCheck && !this.cSt.scopeFindSymbol(newScopedToken.lexeme).isInitialized())
                            semanticWarning(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used before being initialized.");


                        // SEMANTIC ANALYSIS - Identifier used without declaring
                        if (declaredCheck)
                            semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                        else {
                            Identifier unresolvedIdentifier = new Identifier(
                                    this.globalSt.getName(),
                                    newToken.lexeme,
                                    newToken.lineNumber
                            );
                            this.unresolvedIdentifiers.add(unresolvedIdentifier);
                            return unresolvedIdentifier;
                        }
                    } else {
                        return new Identifier(
                                this.globalSt.findSymbol(newScopedToken.lexeme),
                                this.cSt.scopeFindSymbol(newToken.lexeme),
                                newScopedToken.lineNumber
                        );
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get the symbol that references 'this' in the current non-static subroutine.
     * @return the 'this' symbol
     */
    private Symbol getClassObject() {
        boolean isMethod = this.subSt.getSymbol().getKind() == Symbol.Kind.METHOD;
        boolean isConstructor = this.subSt.getSymbol().getKind() == Symbol.Kind.CONSTRUCTOR;

        return (isMethod || isConstructor) ? this.cSt.scopeFindSymbol("this") : null;
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
    private Symbol parseVariableDeclaration(boolean singleIdentifierOnly, boolean isInitialized, String kind) throws ParserException, IOException {
        String type = parseType();
        Token identifier = this.t.getNextToken();
        Symbol symbol;

        // SEMANTIC ANALYSIS - Check for redeclaration
        if (this.cSt.subroutineContains(identifier.lexeme))
            semanticError(this.t.peekNextToken().lineNumber, "Redeclaration of identifier: " + identifier.lexeme);

        symbol = this.cSt.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(kind), isInitialized).getSymbol();

        // Some variable declarations allow for multiple variables at once to be declared
        if (!singleIdentifierOnly) {
            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                identifier = this.t.getNextToken();
                this.cSt.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(kind), isInitialized);
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
    private boolean parseStatementBody() throws ParserException, IOException {
        boolean returnsOnAllCodePaths = false;

        if (!this.t.peekNextToken().lexeme.equals("}")) {
            while (!this.t.peekNextToken().lexeme.equals("}")) {
                returnsOnAllCodePaths = parseStatement();
            }
        }

        return returnsOnAllCodePaths;
    }

    /**
     * SEMANTIC ANALYSIS - Check that the arguments provided to a subroutine are in line with the function definition.
     *
     * @param identifier the identifier that the arguments are a part of.
     * @param paramTypes a list of parameter types that have been found.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void checkSubroutineArguments(Identifier identifier, LinkedList<String> paramTypes) throws IOException {
        // If the subroutine has not yet been declared or is not part of this class then don't check argument types.
        if (identifier != null && identifier.getId() != null) {
            Symbol.Kind subroutineKind = identifier.getKind();
            if (subroutineKind == Symbol.Kind.METHOD || subroutineKind == Symbol.Kind.CONSTRUCTOR
                    || subroutineKind == Symbol.Kind.FUNCTION) {
                SymbolTable subroutine = identifier.getId().getChildSymbolTable();
                LinkedList<String> subroutineSymbolTypes = subroutine.getArgumentSymbols();

                // SEMANTIC ANALYSIS - Check that the number of arguments
                if (subroutineKind == Symbol.Kind.METHOD && paramTypes.size() != subroutine.getArgumentCount() - 1)
                    semanticError(this.t.peekNextToken().lineNumber, "The number of arguments for the function call doesn't match that of the declaration.");
                else if ((subroutineKind == Symbol.Kind.CONSTRUCTOR || subroutineKind == Symbol.Kind.FUNCTION) &&
                        paramTypes.size() != subroutine.getArgumentCount())
                    semanticError(this.t.peekNextToken().lineNumber, "The number of arguments for the function call doesn't match that of the declaration.");

                // SEMANTIC ANALYSIS - Check that arguments match.
                Iterator<String> it1 = paramTypes.iterator();
                Iterator<String> it2 = subroutineSymbolTypes.iterator();
                while (it1.hasNext() && it2.hasNext()) {
                    String currentType = it1.next();
                    String declarType = it2.next();

                    if (!currentType.equals(declarType) && !declarType.equals("int"))
                        semanticError(this.t.peekNextToken().lineNumber, "The type: " + currentType + " doesn't match " + declarType + " used in the function declaration.");
                }
            }
        // Add the parameter types to be resolved and checked later
        } else if (identifier != null){
            identifier.setArguments(paramTypes);
        }
    }

    /**
     * Parse a conditional statement of the form (condition).
     * This is used as part of a while loop or if statement.
     *
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     */
    private void parseConditionalStatement() throws IOException, ParserException {
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
    }

    /**
     * Delete the vm file that has been created. This function is run
     * if the compiler finds a semantic or parsing error and must remove the
     * vm code that has been generated up to the point of the exception.
     */
    public void deleteVMCode() {
        this.w.deleteFile();
    }
}


/**
 * The identifier class is used to handle the various types of identifiers
 * that are allowed in the jack language. It stores information about the
 * identifier and allows for quick access to the data stored in the symbol table
 * about it.
 */
class Identifier {
    private Symbol cId;
    private Symbol id;
    private String cIdName;
    private String idName;
    private int lineNumber;
    private LinkedList<String> arguments;

    /**
     * Create an identifier that isn't yet declared in the symbol table.
     *
     * @param cIdName the name of the class level identifier.
     * @param idName the name of the identifier.
     * @param lineNumber the line number the identifier is located at.
     */
    public Identifier(String cIdName, String idName, int lineNumber) {
        this(cIdName, null, idName, null, lineNumber);
    }

    /**
     * Create an identifier that has been declared, fully.
     *
     * @param cId the class identifier symbol.
     * @param id the identifier symbol.
     * @param lineNumber the line number that the identifier is located at.
     */
    public Identifier(Symbol cId, Symbol id, int lineNumber) {
        this(cId.getName(), cId, id.getName(), id, lineNumber);
    }

    /**
     * Create an identifier.
     *
     * @param cIdName the name of the class level identifier.
     * @param cId the class identifier symbol.
     * @param idName the name of the identifier.
     * @param id the identifier symbol.
     * @param lineNumber the line number the identifier is located at.
     */
    public Identifier(String cIdName, Symbol cId, String idName, Symbol id, int lineNumber) {
        this.cIdName = cIdName;
        this.cId = cId;
        this.idName = idName;
        this.id = id;
        this.lineNumber = lineNumber;
    }

    /*
     * Getters
     */
    public String getCIdName() { return this.cIdName; }
    public String getIdName() { return this.idName; }
    public int getLineNumber() { return this.lineNumber; }
    public Symbol getId() { return this.id; }
    public Symbol getCId() { return this.cId; }
    public LinkedList<String> getArguments() { return this.arguments; }

    // these getters allow quick access of data i.e. rather than typing identifier.getId().getKind() -> identifier.getKind()
    public Symbol.Kind getKind() { return this.id != null ? this.id.getKind() : null; }
    public int getIndex() { return this.id != null ? this.id.getIndex() : -1; }
    public String getType() { return this.id != null ? this.id.getType() : ""; }

    /*
     * Setters
     */
    public void setArguments(LinkedList<String> args) {
        this.arguments = args;
    }
    public void setId(Symbol symbol) {
        this.id = symbol;
    }
    public void setInitialized(boolean initialized) { this.id.setInitialized(initialized); }

    /**
     * Return the name of the identifier.
     * @return name of the identifier.
     */
    @Override
    public String toString() {
        if (this.cIdName == null || this.cIdName.equals(""))
            return this.idName;
        else
            return this.cIdName + "." + this.idName;
    }
}