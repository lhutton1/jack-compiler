package com;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

//TODO
// - make sure that function calls have the same number of arguments as the declaration.
// - vm code for do statements with functions outside of the class.
// - functions declared after calling function are accepted.
// - type checking on return statements and other things need checking.
// - add checks to make sure method not called as function/constructor and visa versa.


public class CompilationEngine {
    private final Tokenizer t;
    private final VMWriter w;
    private SymbolTable globalSymbolTable;
    private SymbolTable currentSubroutineTable;
    private SymbolTable currentSymbolTable;
    private ArrayList<Identifier> unresolvedIdentifiers;
    private int labelCounter;
    private boolean semanticStatus;

    private final boolean DEBUGGING = true;
    private final boolean SEMANTIC_ANALYSIS = true;

    public CompilationEngine(File file) throws IOException {
        this.t = new Tokenizer(file);
        this.w = new VMWriter(file);
        this.unresolvedIdentifiers = new ArrayList<>();
        this.semanticStatus = true;
    }

    public boolean getSemanticStatus() { return this.semanticStatus; }

    public void semanticError(int lineNumber, String msg) {
        if (SEMANTIC_ANALYSIS) {
            System.err.println("[Semantic error] Line " + lineNumber + ": " + msg);
            semanticStatus = false;
        }
    }

    public void run() throws IOException, ParserException, SemanticException {
        // Parse a single class
        this.parseClass();

        // SEMANTIC ANALYSIS - Check to make sure that only one class has been created
        if (this.t.peekNextToken().type != Token.Types.EOF) {
            semanticError(this.t.peekNextToken().lineNumber, "Expected end of file, only one class per file.");
        }

        // Only run if no semantic errors have been output
        if (DEBUGGING) {
            this.globalSymbolTable.printTables();
            System.out.println("Unresolved identifiers: " + this.unresolvedIdentifiers + " <to be solved by semantic analysis>");
        }

        // Compilation successful
        if (semanticStatus)
            System.out.println("Compilation successfully completed!");

        // Close the writer
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

        // Symbol table is yet to be initialized so instead of calling
        // parseIdentifier() parse the symbol here
        Token identifier = this.t.getNextToken();
        if (identifier.type != Token.Types.IDENTIFIER)
            throw new ParserException(identifier.lineNumber, "Expected IDENTIFIER got: " + identifier.lexeme);

        // After the class name has been retrieved we can set up the SYMBOL table
        Symbol rootSymbol = new Symbol(identifier.lexeme, identifier.lexeme, Symbol.Kind.CLASS, 0, true);
        this.globalSymbolTable = new SymbolTable(null, rootSymbol);
        this.currentSymbolTable = this.globalSymbolTable;

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
    private void parseMemberDeclaration() throws ParserException, SemanticException, IOException {
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
    private void parseClassVarDeclaration() throws ParserException, SemanticException, IOException {
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
    private void parseSubRoutineDeclaration() throws ParserException, SemanticException, IOException {
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
        if (this.currentSymbolTable.contains(identifier.lexeme))
            throw new ParserException(identifier.lineNumber, "Redeclaration of identifier: " + identifier.lexeme);
        else {
            ChildSymbol newFunctionSymbol = this.currentSymbolTable.addSymbol(
                    identifier.lexeme,
                    type,
                    Symbol.Kind.fromString(functionType),
                    true
            );

            this.currentSymbolTable = newFunctionSymbol.getSymbolTable();
            this.currentSubroutineTable = this.currentSymbolTable;
        }

        parseSymbol("(");
        parseParamList();
        parseSymbol(")");
        parseSymbol("{");
        boolean returnsAllCodePaths = parseSubroutineBody();

        // VM CODE - generate the function
        this.w.writeLine("function " + this.globalSymbolTable.getName() + "."
                + identifier.lexeme + " " + this.currentSymbolTable.getLocalCount());
        if (functionType.equals("constructor")) {
            // We need to allocate memory for the object and obtain a
            // pointer for 'this'.
            this.w.writeLine("push constant " + this.globalSymbolTable.getFieldCount());
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
        this.currentSymbolTable = this.currentSymbolTable.getParent();
    }

    /**
     * Parse the parameter list.
     * paramList → type IDENTIFIER {, type IDENTIFIER} | ε
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseParamList() throws ParserException, SemanticException, IOException {
        //LinkedList<Symbol> symbols = new LinkedList<>();

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
    private boolean parseSubroutineBody() throws ParserException, SemanticException, IOException {
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
    private boolean parseStatement() throws ParserException, SemanticException, IOException {
        boolean returnsOnAllCodePaths = false;
        Token statementStart = this.t.peekNextToken();

        switch (statementStart.lexeme) {
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
                throw new ParserException(statementStart.lineNumber, "Expected statement. Got: " + statementStart.lexeme);
        }

        return returnsOnAllCodePaths;
    }

    /**
     * Parse a variable declaration within a statement.
     * varDeclarStatement → var type IDENTIFIER { , IDENTIFIER } ;
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
        Identifier identifier;
        boolean isArrayIdentifier = false;

        parseKeyword("let");
        identifier = parseIdentifier(true, false);

        // Check if the identifier has an array index after it
        if (this.t.peekNextToken().lexeme.equals("[")) {
            isArrayIdentifier = true;
            this.t.getNextToken();

            returnType = parseExpression();

            // VM CODE - Add array index to vm code
            if (identifier != null) {
                this.w.writeLater("push " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getKind().toString() +
                        " " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getIndex());
                this.w.writeLater("add");
            }

            // SEMANTIC ANALYSIS - Check that an array index evaluates to an integer constant
            if (identifier != null && !returnType.equals("int"))
                semanticError(identifier.getLineNumber(), "Expression in array indices must always evaluate to an integer.");

            parseSymbol("]");
        }

        parseSymbol("=");
        returnType = parseExpression();

        // SEMANTIC ANALYSIS - Check type matches LHS.
        // Things to note:
        // - Array can support any data type.
        // - Int can be indirectly converted to a boolean.
        if (identifier != null && identifier.getType().equals("boolean") && returnType.equals("int"))
            returnType = "boolean";
        if (identifier != null && !returnType.equals("") && !returnType.equals(identifier.getType()) && !identifier.getType().equals("Array"))
            semanticError(identifier.getLineNumber(), "Cannot assign type " + returnType + " to " + identifier.getType() + ".");

        parseSymbol(";");

        // VM CODE - Update the identifier value
        if (identifier != null && !isArrayIdentifier) {
            this.w.writeLater("pop " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getKind().toString() +
                    " " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getIndex());
        } else {
            this.w.writeLater("pop temp 0");
            this.w.writeLater("pop pointer 1");
            this.w.writeLater("push temp 0");
            this.w.writeLater("pop that 0");
        }

        // Initialize the variable
        if (identifier != null && this.currentSymbolTable.hierarchyContains(identifier.getIdentifier()))
            this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).setInitialized(true);
        // SEMANTIC ANALYSIS - Identifier has not been declared in this scope
        else if (identifier != null)
            semanticError(identifier.getLineNumber(), "Identifier " + identifier.getIdentifier() + " used without previously declaring.");
    }

    /**
     * Parse an if statement.
     * ifStatement → if ( expression ) { {statement} } [else { {statement} }]
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private boolean parseIfStatement() throws ParserException, SemanticException, IOException {
        boolean returnsOnAllCodePaths;
        boolean elseReturnsOnAllCodePaths = true;

        // Increment label counter to generate unique label value
        int labelValue = this.labelCounter++;

        // Add the if statement to the symbol table
        ChildSymbol childSymbol = this.currentSymbolTable.addSymbol(
            "",
            "if-stmt",
            Symbol.Kind.INNER,
            true
        );
        this.currentSymbolTable = childSymbol.getSymbolTable();

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
        // Increment label counter to generate unique label value
        int labelValue = this.labelCounter++;

        // Add the while statement to the symbol table
        ChildSymbol childSymbol = this.currentSymbolTable.addSymbol(
                "",
                "while-stmt",
                Symbol.Kind.INNER,
                true
        );
        this.currentSymbolTable = childSymbol.getSymbolTable();

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
    private void parseReturnStatement() throws ParserException, SemanticException, IOException {
        String type = "";
        parseKeyword("return");

        if (!this.t.peekNextToken().lexeme.equals(";"))
            type = parseExpression();

        // TODO remove blank return type
        // SEMANTIC ANALYSIS - Check return type matches function declaration
        type = type.equals("") ? "void" : type;
        if (this.currentSubroutineTable != null && !this.currentSubroutineTable.getInfo().getType().equals(type))
            semanticError(this.t.peekNextToken().lineNumber, "Return type " + type + " not compatible with return type specified in function declaration.");

        // VM CODE - Push void and return
        if (this.currentSubroutineTable != null && this.currentSubroutineTable.getType().equals("void"))
            this.w.writeLater("push constant 0");
        this.w.writeLater("return");

        parseSymbol(";");

        // SEMANTIC ANALYSIS - Check for unreachable code
        Token unreachable = this.t.peekNextToken();
        if (!unreachable.lexeme.equals("}"))
            semanticError(unreachable.lineNumber, "Unreachable code will not be executed.");
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private void parseSubroutineCall() throws ParserException, SemanticException, IOException {
        parseSubroutineCall(parseIdentifier(false, true));
    }

    /**
     * Parse a subroutine call.
     * subroutineCall → identifier [ . identifier ] ( expressionList ) ;
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    public void parseSubroutineCall(Identifier identifier) throws ParserException, SemanticException, IOException {
        int offset = 0;

        parseSymbol("(");

        // VM CODE - Write code to call a subroutine
        // if method invocation in the same class
        if (identifier.getClassIdentifier().equals(this.globalSymbolTable.getName()) || identifier.getClassIdentifier().equals("")) {
            if (this.currentSubroutineTable.getKind() == Symbol.Kind.METHOD || this.currentSubroutineTable.getKind() == Symbol.Kind.CONSTRUCTOR) {
                if (identifier.getClassIdentifierSymbol() != null && this.currentSymbolTable.hierarchyContains(identifier.getClassIdentifierSymbol().getName())) {
                    this.w.writeLater("push " + this.currentSymbolTable.findHierarchySymbol(identifier.getClassIdentifierSymbol().getName()).getKind().toString() + " "
                            + this.currentSymbolTable.findHierarchySymbol(identifier.getClassIdentifierSymbol().getName()).getIndex());
                    offset = 1;
                } else if (identifier.getClassIdentifierSymbol() != null && identifier.getClassIdentifierSymbol().getName().equals("this")) {
                    this.w.writeLater("push pointer 0");
                    offset = 1;
                }
            }

        // If method invocation is in another class
        } else {
            // Check to see if the first identifier needs to be pushed
            if (identifier.getClassIdentifierSymbol() != null && this.currentSymbolTable.hierarchyContains(identifier.getClassIdentifierSymbol().getName())) {
                this.w.writeLater("push " + identifier.getClassIdentifierSymbol().getKind().toString() + " " + identifier.getClassIdentifierSymbol().getIndex());
                offset = 1;
            }

            // Check if the second identifier exists and push it
            if (identifier.getKind() != null) {
                this.w.writeLater("push " + identifier.getKind().toString() + " " + identifier.getIndex());
            }
        }

        // SEMANTIC ANALYSIS - Check that the subroutine arguments match.
        ArrayList<String> paramList = parseExpressionList();
        checkSubroutineArguments(identifier, paramList);

        // VM CODE - Call identifier
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
    private String parseRelationalExpression() throws ParserException, SemanticException, IOException {
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
    private String parseArithmeticExpression() throws ParserException, SemanticException, IOException {
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
    private String parseTerm() throws ParserException, SemanticException, IOException {
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
    private String parseFactor() throws ParserException, SemanticException, IOException {
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
     * operand → integerConstant | IDENTIFIER [.IDENTIFIER ] [ [ expression ] | (expressionList) ] | (expression) | stringLiteral | true | false | null | this | subroutineCall
     * (edited to allow this KEYWORD to represent a class variable)
     *
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private String parseOperand() throws ParserException, SemanticException, IOException {
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
            Identifier identifier = parseIdentifier(true, true);
            type = identifier != null ? identifier.getType() : "";

            // VM CODE - If the token type is not part of an array or subroutine call
            if (!this.t.peekNextToken().lexeme.equals("[") && !this.t.peekNextToken().lexeme.equals("(")) {
                if (identifier != null) {
                    // Try to resolve the symbol based on it already being in the symbol table
                    if ((identifier.getClassIdentifier().equals("") || identifier.getClassIdentifier().equals(this.globalSymbolTable.getName()))
                            && this.currentSymbolTable.hierarchyContains(identifier.getIdentifier())) {
                        this.w.writeLater("push " + identifier.getKind() + " " + identifier.getIndex());

                        // TODO check to see if this is actually needed, can it go ^^?
                        // Special case for constructor due to 'return this' being possible in constructor
                    } else if (typeToken.lexeme.equals("this") && !this.t.peekNextToken().lexeme.equals(".")
                            && this.currentSubroutineTable.getKind() == Symbol.Kind.CONSTRUCTOR)
                        this.w.writeLater("push pointer 0");
                }
            }

            // If the token is part of an array index
            else if (this.t.peekNextToken().lexeme.equals("[")) {
                Token arrayIndexStart = this.t.getNextToken();
                String arrayIndexType = parseExpression();

                // VM CODE - Write the identifier
                if (identifier != null) {
                    this.w.writeLater("push " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getKind().toString() +
                            " " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getIndex());
                    this.w.writeLater("add");
                }

                if (!arrayIndexType.equals("int"))
                    semanticError(arrayIndexStart.lineNumber, "Expression in array indices must always evaluate to an integer. Type received was, " + arrayIndexType);

                parseSymbol("]");

                // VM CODE - Handle the array index
                this.w.writeLater("pop pointer 1");
                this.w.writeLater("push that 0");
                type = "Array";

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
     * @param keyword the KEYWORD to be parsed.
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
     * Parse an Identifier.
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
            if (!this.currentSymbolTable.hierarchyContains(newToken.lexeme) && !newToken.lexeme.equals("this") ) {
                // SEMANTIC ANALYSIS - Identifier used without declaring
                if (declaredCheck)
                    semanticError(newToken.lineNumber, "Identifier " + newToken.lexeme + " used without previously declaring.");

                // If we don't want to check whether the identifier is declared, add it to the unresolved identifiers
                else {
                    Identifier unresolvedIdentifier = new Identifier(
                            this.globalSymbolTable.getName(),
                            newToken.lexeme,
                            newToken.lineNumber,
                            null,
                            getClassObject()
                    );
                    this.unresolvedIdentifiers.add(unresolvedIdentifier);
                    return unresolvedIdentifier;
                }

            // SEMANTIC ANALYSIS - Check to see if the symbol has been initialized
            } else if (initializedCheck && !this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).isInitialized())
                semanticError(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

            // At this point we know the identifier is in the symbol table so return it
            else {
                return new Identifier(
                        this.globalSymbolTable.getName(),
                        newToken.lexeme,
                        newToken.lineNumber,
                        this.currentSymbolTable.findHierarchySymbol(newToken.lexeme),
                        getClassObject()
                );
            }
        }

        // Class identifier
        else {
            this.t.getNextToken(); // Skip the '.'
            Token newScopedToken = this.t.getNextToken();

            // Check to see if the class level identifier has been declared (it could be a variable or the name of a class)
            if (!this.currentSymbolTable.hierarchyContains(newToken.lexeme) && !newToken.lexeme.equals(this.globalSymbolTable.getName())) {
                Identifier unresolvedIdentifier = new Identifier(
                        newToken.lexeme,
                        newScopedToken.lexeme,
                        newToken.lineNumber
                );
                this.unresolvedIdentifiers.add(unresolvedIdentifier);
                return unresolvedIdentifier;

            // SEMANTIC ANALYSIS - Check to see if the class level symbol has been initialized
            } else if (initializedCheck && !this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).isInitialized())
                semanticError(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

            // We now know that the outer identifier has been defined so we can proceed to check the inner identifier
            // This case covers when the class identifier is of the current class
            else if (newToken.lexeme.equals(this.globalSymbolTable.getName())) {
                if (!this.globalSymbolTable.contains(newScopedToken.lexeme)) {
                    // SEMANTIC ANALYSIS - Identifier used without declaring
                    if (declaredCheck)
                        semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                    else {
                        Identifier unresolvedIdentifier = new Identifier(
                                this.globalSymbolTable.getName(),
                                newScopedToken.lexeme,
                                newScopedToken.lineNumber
                        );
                        this.unresolvedIdentifiers.add(unresolvedIdentifier);
                        return unresolvedIdentifier;
                    }
                } else {
                    return new Identifier(
                            newToken.lexeme,
                            newScopedToken.lexeme,
                            newScopedToken.lineNumber,
                            this.globalSymbolTable.findSymbol(newScopedToken.lexeme),
                            this.currentSymbolTable.findHierarchySymbol(newToken.lexeme)
                    );
                }

            // Covers the case when the identifier is a variable identifier referencing a class
            } else {
                // SEMANTIC ANALYSIS - Check that the identifier has been initialized since it is now known to be a variable
                if (initializedCheck && !this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).isInitialized())
                    semanticError(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

                // Check the inner identifier
                String classType = this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).getType();

                // If the inner identifier is part of another class
                if (!this.globalSymbolTable.getName().equals(classType)) {
                    // SEMANTIC ANALYSIS - Identifier used without declaring
                    if (declaredCheck)
                        semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                    else {
                        Identifier unresolvedIdentifier = new Identifier(
                                this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).getType(),
                                newScopedToken.lexeme,
                                newToken.lineNumber,
                                null,
                                this.currentSymbolTable.findHierarchySymbol(newToken.lexeme)
                        );
                        this.unresolvedIdentifiers.add(unresolvedIdentifier);
                        return unresolvedIdentifier;
                    }
                // Otherwise, it is referencing the current class
                } else {
                    if (!this.globalSymbolTable.contains(newScopedToken.lexeme)) {
                        // SEMANTIC ANALYSIS - Check that the identifier has been initialized since it is now known to be a variable
                        if (initializedCheck && !this.currentSymbolTable.findHierarchySymbol(newScopedToken.lexeme).isInitialized())
                            semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used before being initialized.");


                        // SEMANTIC ANALYSIS - Identifier used without declaring
                        if (declaredCheck)
                            semanticError(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                        else {
                            Identifier unresolvedIdentifier = new Identifier(
                                    this.globalSymbolTable.getName(),
                                    newToken.lexeme,
                                    newToken.lineNumber
                            );
                            this.unresolvedIdentifiers.add(unresolvedIdentifier);
                            return unresolvedIdentifier;
                        }
                    } else {
                        return new Identifier(
                                classType,
                                newScopedToken.lexeme,
                                newScopedToken.lineNumber,
                                this.globalSymbolTable.findSymbol(newScopedToken.lexeme),
                                this.currentSymbolTable.findHierarchySymbol(newToken.lexeme)
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
        boolean isMethod = this.currentSubroutineTable.getInfo().getKind() == Symbol.Kind.METHOD;
        boolean isConstructor = this.currentSubroutineTable.getInfo().getKind() == Symbol.Kind.CONSTRUCTOR;

        return (isMethod || isConstructor) ? this.currentSymbolTable.findHierarchySymbol("this") : null;
    }

    /**
     * Parse a variable declaration.
     * Within this method we also need to update the SYMBOL table to be able to determine in the future
     * whether a variable has previously been declared.
     *
     * @param singleIdentifierOnly whether to continue searching for more identifiers or just detect a single IDENTIFIER.
     * @param kind the kind of the variable i.e. static, field, argument, var.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
    private Symbol parseVariableDeclaration(boolean singleIdentifierOnly, boolean isInitialized, String kind) throws ParserException, SemanticException, IOException {
        String type = parseType();
        Token identifier = this.t.getNextToken();
        Symbol symbol;

        // SEMANTIC ANALYSIS - Check for redeclaration
        if (this.currentSymbolTable.subroutineContains(identifier.lexeme))
            semanticError(this.t.peekNextToken().lineNumber, "Redeclaration of identifier: " + identifier.lexeme);

        symbol = this.currentSymbolTable.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(kind), isInitialized).getSymbol();

        // Some variable declarations allow for multiple variables at once to be declared
        if (!singleIdentifierOnly) {
            while (this.t.peekNextToken().lexeme.equals(",")) {
                this.t.getNextToken();
                identifier = this.t.getNextToken();
                this.currentSymbolTable.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(kind), isInitialized);
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

    /**
     * SEMANTIC ANALYSIS - Check that the arguments provided to a subroutine are in line with the function definition
     * @param identifier
     * @param paramTypes
     * @throws SemanticException
     * @throws IOException
     */
    private void checkSubroutineArguments(Identifier identifier, ArrayList<String> paramTypes) throws IOException {
        // If the subroutine has not yet been declared or is not part of this class then don't check argument types.
        if (identifier.getIdentifierSymbol() != null) {
            Symbol.Kind subroutineKind = identifier.getIdentifierSymbol().getKind();
            if (subroutineKind == Symbol.Kind.METHOD || subroutineKind == Symbol.Kind.CONSTRUCTOR
                    || subroutineKind == Symbol.Kind.FUNCTION) {
                SymbolTable subroutine = identifier.getIdentifierSymbol().getChildSymbolTable();
                HashMap<Integer, String> subroutineSymbolTypes = subroutine.getArgumentSymbols();

                // SEMANTIC ANALYSIS - Check that the number of arguments
                if (paramTypes.size() != subroutine.getArgumentCount() - 1)
                    semanticError(this.t.peekNextToken().lineNumber, "The number of arguments for the function call doesn't match that of the declaration.");

                // SEMANTIC ANALYSIS - Check that arguments match.
                for (int x = 0; x < paramTypes.size(); x++) {
                    if (!paramTypes.get(x).equals(subroutineSymbolTypes.get(x+1)))
                        semanticError(this.t.peekNextToken().lineNumber, "The type: " + paramTypes.get(x) + " doesn't match the type used in the function declaration.");
                }
            }
        }
    }

    /**
     *
     * @throws IOException
     * @throws ParserException
     * @throws SemanticException
     */
    private void parseConditionalStatement() throws IOException, ParserException, SemanticException {
        parseSymbol("(");
        parseExpression();
        parseSymbol(")");
    }
}

class Identifier {
    private String classIdentifier;
    private Symbol classIdentifierSymbol;
    private String identifier;
    private Symbol identifierSymbol;
    private int lineNumber;

    public Identifier(String classIdentifier, String identifier, int lineNumber) {
        this(classIdentifier, identifier, lineNumber, null, null);
    }

    public Identifier(String classIdentifier, String identifier, int lineNumber, Symbol identifierSymbol, Symbol classIdentifierSymbol) {
        this.classIdentifier = classIdentifier;
        this.identifier = identifier;
        this.lineNumber = lineNumber;
        this.identifierSymbol = identifierSymbol;
        this.classIdentifierSymbol = classIdentifierSymbol;
    }

    public String getClassIdentifier() { return this.classIdentifier; }
    public String getIdentifier() { return this.identifier; }
    public int getLineNumber() { return this.lineNumber; }
    public Symbol getIdentifierSymbol() { return this.identifierSymbol; }
    public Symbol getClassIdentifierSymbol() { return this.classIdentifierSymbol; }

    public Symbol.Kind getKind() {
        if (this.identifierSymbol != null)
            return this.identifierSymbol.getKind();
        else
            return null;
    }

    public int getIndex() {
        if (this.identifierSymbol != null)
            return this.identifierSymbol.getIndex();

        return -1;
    }

    public String getType() {
        if (this.identifierSymbol != null)
            return this.identifierSymbol.getType();

        return "";
    }

    public String toString() {
        if (this.classIdentifier == null || this.classIdentifier.equals(""))
            return this.identifier;
        else
            return this.classIdentifier + "." + this.identifier;
    }
}