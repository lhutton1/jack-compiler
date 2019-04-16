package com;

import sun.jvm.hotspot.debugger.cdbg.Sym;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

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

    private final boolean SEMANTIC_ANALYSIS = true;

    /**
     * Create a new instance of the parser. This takes a stream of tokens from the
     * lexical analyzer and determines if the source code contains any grammatical errors.
     *
     * @param filePath path of the source code that needs to be parsed.
     * @throws ParserException, ParserException thrown if the parser runs into a syntax error and must stop.
     * @throws IOException, IOException thrown if the tokenizer runs into an issue reading the source code.
     */
//    public CompilationEngine(String filePath) throws IOException, ParserException, SemanticException {
//        this.t = new Tokenizer(filePath);
//        this.globalSymbolTable = new SymbolTable();
//        this.currentSymbolTable = this.globalSymbolTable;
//        this.unresolvedIdentifiers = new ArrayList<>();
//        this.labelCounter = 0;
//
//        //while (this.t.peekNextToken().type != Token.TokenTypes.EOF)
//            this.parseClass();
//
//        System.out.println("There are no syntax errors");
//        this.globalSymbolTable.printTables();
//        System.out.println(this.unresolvedIdentifiers);
//    }

    public CompilationEngine(File file) throws IOException, ParserException, SemanticException {
        this.t = new Tokenizer(file);
        this.w = new VMWriter(file);
        this.unresolvedIdentifiers = new ArrayList<>();
    }

    public void run() throws IOException, ParserException, SemanticException {
        this.parseClass();
        this.globalSymbolTable.printTables();
        System.out.println(this.unresolvedIdentifiers);


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

        // symbol table is yet to be initialized so instead of calling parseIdentifier we just parse it here
        Token identifier = this.t.getNextToken();
        if (identifier.type != Token.TokenTypes.identifier)
            throw new ParserException(identifier.lineNumber, "Expected identifier got: " + identifier.lexeme);

        // after the class name has been retrieved we can set up the symbol table
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

        String kind  = token.lexeme.equals("field") ? "this" : token.lexeme;
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
        String functionType = this.t.getNextToken().lexeme;
        String type;
        Token identifier;
        LinkedList<Symbol> symbols;

        if (!functionType.equals("constructor") && !functionType.equals("function") && !functionType.equals("method"))
            throw new ParserException(this.t.peekNextToken().lineNumber, "Expected function declaration. Got: " + functionType);

        // resolve the type of the function
        if (this.t.peekNextToken().lexeme.equals("void")) {
            this.t.getNextToken();
            type = "void";
        } else
            type = parseType();

        // now resolve the identifier
        if (this.t.peekNextToken().type != Token.TokenTypes.identifier)
            throw new ParserException(this.t.peekNextToken().lineNumber, "Expected identifier. Got: " + this.t.peekNextToken().lexeme);
        identifier = this.t.getNextToken();

        // check for redeclaration and add symbol
        if (this.currentSymbolTable.contains(identifier.lexeme))
            throw new ParserException(identifier.lineNumber, "Redeclaration of identifier: " + identifier.lexeme);
        else {
            this.currentSymbolTable = this.currentSymbolTable.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(functionType), true).getSymbolTable();
            this.currentSubroutineTable = this.currentSymbolTable;
        }

        parseSymbol("(");
        symbols = parseParamList();
        parseSymbol(")");

        boolean returnsAllCodePaths = parseSubroutineBody();

        // VM CODE - generate the function
        this.w.writeLine("function " + this.globalSymbolTable.getName() + "." + identifier.lexeme + " " + this.currentSymbolTable.getLocalCount());
        if (functionType.equals("constructor")) {
            this.w.writeLine("push constant " + this.globalSymbolTable.getFieldCount());
            this.w.writeLine("call Memory.alloc 1");
            this.w.writeLine("pop pointer 0");
        } else if (functionType.equals("method")) {
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
                throw new ParserException(token.lineNumber, "Expected statement. Got: " + token.lexeme);
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
        Identifier identifier = parseIdentifier(true, false);
        boolean isArrayIdentifier = false;

        if (identifier == null)
            throw new SemanticException("UNDECLARED - please check.");

        if (this.t.peekNextToken().lexeme.equals("[")) {
            isArrayIdentifier = true;
            this.t.getNextToken();

            returnType = parseExpression();

            this.w.writeLater("push " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getKind().toString() +
                    " " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getIndex());
            this.w.writeLater("add");

            //if (!returnType.equals("int"))
            //    throw new SemanticException(identifier.getLineNumber(), "Expression in array indices must always evaluate to an integer.");

            parseSymbol("]");

            // VM CODE
//
        }


        parseSymbol("=");

        // SEMANTIC ANALYSIS - check type matches LHS.
        returnType = parseExpression();
//        if (!returnType.equals("") && !returnType.equals("Array") && !returnType.equals(this.currentSymbolTable.findHierarchySymbol(identifier.lexeme).getType()))
//            throw new SemanticException(identifier.lineNumber, "Cannot assign type " + returnType + " to "
//                    + identifier.lexeme + ".");

        parseSymbol(";");

        if (!isArrayIdentifier) {
            // VM CODE - pop variable
            this.w.writeLater("pop " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getKind().toString() +
                    " " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getIndex());
        } else {
            // VM CODE
            this.w.writeLater("pop temp 0");
            this.w.writeLater("pop pointer 1");
            this.w.writeLater("push temp 0");
            this.w.writeLater("pop that 0");
        }

        // the variable has now been initialized
        try {
            this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).setInitialized(true);
        } catch (NullPointerException e) {
            throw new SemanticException(identifier.getLineNumber(), "Identifier " + identifier.getIdentifier() + " used without previously declaring.");
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
        int labelValue = this.labelCounter++;
        boolean returnsOnAllCodePaths = false;
        boolean elseReturnsOnAllCodePaths = false;
        this.currentSymbolTable = this.currentSymbolTable.addSymbol("", "if-stmt", Symbol.Kind.INNER, true).getSymbolTable();

        parseKeyword("if");
        parseConditionalStatment();

        // VM CODE - write if statement code
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

        if (this.t.peekNextToken().lexeme.equals("else")) {
            this.t.getNextToken();
            parseSymbol("{");
            elseReturnsOnAllCodePaths = parseStatementBody();
            parseSymbol("}");

            // VM CODE - write if statement code
            this.w.writeLater("label IF_END" + labelValue);
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
        int labelValue = this.labelCounter++;
        this.currentSymbolTable = this.currentSymbolTable.addSymbol("", "while-stmt", Symbol.Kind.INNER, true).getSymbolTable();

        parseKeyword("while");

        // VM CODE - write while statement to vm
        this.w.writeLater("label WHILE_EXP" + labelValue);

        parseConditionalStatment();

        // VM CODE - write while statement to vm
        this.w.writeLater("not");
        this.w.writeLater("if-goto WHILE_END" + labelValue);

        parseSymbol("{");
        parseStatementBody();
        parseSymbol("}");

        // VM CODE - write while statement to vm
        this.w.writeLater("goto WHILE_EXP" + labelValue);
        this.w.writeLater("label WHILE_END" + labelValue);

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

        // TODO semantic analysis for same return type
        // SEMANTIC ANALYSIS - check return type matches
//        type = type.equals("") ? "void" : type;
//        if (this.currentSubroutineTable != null && !this.currentSubroutineTable.getInfo().getType().equals(type))
//            throw new SemanticException(this.t.peekNextToken().lineNumber + 1, "Return type " + type + " not compatible with return type specified in function declaration."); //TODO check correct +1

        // VM CODE - push void and return
        if (this.currentSubroutineTable.getType().equals("void"))
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
        parseSubroutineCall(parseIdentifier());
    }

    public void parseSubroutineCall(Identifier identifier) throws ParserException, SemanticException, IOException {
        parseSymbol("(");

        // VM CODE
        // if method invocation in the same class
        int offset = 0;
        System.out.println("current function " + this.currentSubroutineTable.getName());
        if (identifier.getClassIdentifierSymbol() != null)
            System.out.println("id: " + identifier.toString() + ", " + identifier.getClassIdentifierSymbol().getName());
        else
            System.out.println("id: " + identifier.toString());
        if (identifier.getClassIdentifier().equals(this.globalSymbolTable.getName()) || identifier.getClassIdentifier().equals("")) {
            System.out.println("same class function");
            if (identifier.getClassIdentifierSymbol() != null)
                System.out.println(identifier.getClassIdentifierSymbol().getName());

            if (this.currentSubroutineTable.getKind() == Symbol.Kind.METHOD || this.currentSubroutineTable.getKind() == Symbol.Kind.CONSTRUCTOR) {
                //System.out.println(identifier.getIdentifier());
                if (identifier.getClassIdentifierSymbol() != null && this.currentSymbolTable.hierarchyContains(identifier.getClassIdentifierSymbol().getName())) {
                    this.w.writeLater("push " + this.currentSymbolTable.findHierarchySymbol(identifier.getClassIdentifierSymbol().getName()).getKind().toString() + " "
                            + this.currentSymbolTable.findHierarchySymbol(identifier.getClassIdentifierSymbol().getName()).getIndex());
                    offset = 1;
                } else if (identifier.getClassIdentifierSymbol() != null && identifier.getClassIdentifierSymbol().getName().equals("this")) {
                    this.w.writeLater("push pointer 0");
                    offset = 1;
                }

            }

            // TODO tidy this up once working
        } else {
            System.out.println("other class function");
            // check to see if the first identifier needs to be pushed
            if (identifier.getClassIdentifierSymbol() != null && this.currentSymbolTable.hierarchyContains(identifier.getClassIdentifierSymbol().getName())) {
                this.w.writeLater("push " + identifier.getClassIdentifierSymbol().getKind().toString() + " " + identifier.getClassIdentifierSymbol().getIndex());
                offset = 1;
            }

            if (identifier.getKind() == null) {
                //this.w.writeLater("push local 0");
               //offset = 1;
            } else {
                this.w.writeLater("push " + identifier.getKind().toString() + " " + identifier.getIndex()); //TODO find out how this changes
            }
        }

        ArrayList<String> paramList = parseExpressionList();
        checkSubroutineArguments(identifier.getIdentifier(), identifier.getClassIdentifier(), paramList);

        // VM CODE - call identifier
        this.w.writeLater("call " + identifier.toString() + " " + (paramList.size() + offset));
        parseSymbol(")");
        System.out.println("-------");
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

        // VM CODE - insert 'this' statement
//        if (this.currentSubroutineTable.getKind() == Symbol.Kind.METHOD) {
//            this.w.writeLater("push " + this.currentSubroutineTable.findSymbol("this").getKind().toString() + " " + this.currentSubroutineTable.findSymbol("this").getIndex());
//        }

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

            // VM CODE - write & / | to vm
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
        Token operator = this.t.peekNextToken();
        String type;

        if (operator.lexeme.equals("-") || operator.lexeme.equals("~"))
            this.t.getNextToken();

        type =  parseOperand();

        // VM CODE - write negate / not to vm
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
                // VM CODE - Write string constant
                this.w.writeLater("push constant " + token.lexeme.length());
                this.w.writeLater("call String.new 1");

                for (char letter : token.lexeme.toCharArray()) {
                    this.w.writeLater("push constant " + (int)letter);
                    this.w.writeLater("call String.appendChar 2");
                }


                type = "stringConstant";
            } else if (token.lexeme.equals("true") || token.lexeme.equals("false")) {
                if (token.lexeme.equals("true")) {
                    this.w.writeLater("push constant 0");
                    this.w.writeLater("not");
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
            Identifier identifier = parseIdentifier();
            type = identifier.getType();
            boolean isSubroutineCall = false;
            boolean isArray = false;

            // VM CODE - Push identifiers
            // we need to determine if a function call has been made or just push the identifier
//            if (identifier.getKind() == Symbol.Kind.METHOD || identifier.getKind() == Symbol.Kind.CONSTRUCTOR || identifier.getKind() == Symbol.Kind.FUNCTION) {
//                System.out.println(identifier.getIdentifier());
//                System.out.println(identifier.getClassIdentifier());
//                this.w.writeLater("CODE FOR FUNCTION CALL");
//            } else {

            if (this.t.peekNextToken().lexeme.equals("[")) {
                String returnType;
                Token arrayIndexStart = this.t.getNextToken();
                isArray = true;
                returnType = parseExpression();

                this.w.writeLater("push " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getKind().toString() +
                        " " + this.currentSymbolTable.findHierarchySymbol(identifier.getIdentifier()).getIndex());
                this.w.writeLater("add");

                //if (!returnType.equals("int"))
                //    throw new SemanticException(arrayIndexStart.lineNumber, "Expression in array indices must always evaluate to an integer.");

                parseSymbol("]");

                // VM CODE
                this.w.writeLater("pop pointer 1");
                this.w.writeLater("push that 0");

                type = "Array";
            } else if (this.t.peekNextToken().lexeme.equals("(")) {
                isSubroutineCall = true;
                parseSubroutineCall(identifier);
            }

            // VM CODE
            if (!isSubroutineCall && !isArray) {
                // try to resolve the symbol based on it already being in the symbol table
                if ((identifier.getClassIdentifier().equals("") || identifier.getClassIdentifier().equals(this.globalSymbolTable.getName()))
                        && this.currentSymbolTable.hierarchyContains(identifier.getIdentifier())) {
                    this.w.writeLater("push " + identifier.getKind() + " " + identifier.getIndex());


                    // special case for constructor due to 'return this'
                } else if (token.lexeme.equals("this") && !this.t.peekNextToken().lexeme.equals(".")
                        && this.currentSubroutineTable.getKind() == Symbol.Kind.CONSTRUCTOR) {
                    this.w.writeLater("push pointer 0");

                    // the symbol could not be found. TODO check unresolved symbol list
                } else
                    this.w.writeLater("UNKNOWN SYMBOL FOUND");
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
    private Identifier parseIdentifier() throws ParserException, SemanticException, IOException {
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
    private Identifier parseIdentifier(boolean declaredCheck, boolean initializedCheck) throws ParserException, SemanticException, IOException {
        Token newToken = this.t.getNextToken();
        boolean isClassIdentifier;

        if (newToken.type != Token.TokenTypes.identifier && !newToken.lexeme.equals("this"))
            throw new ParserException(newToken.lineNumber, "Expected identifier. Got: " + newToken.lexeme);

        // we need to decide whether the identifier has a class scope
        isClassIdentifier = this.t.peekNextToken().lexeme.equals(".");

        // Single identifier
        if (!isClassIdentifier) {
            System.out.println("not a class level identifier");
            // SEMANTIC ANALYSIS - this case covers the chance that we may run into a function / identifier that has yet to be declared
            if (!this.currentSymbolTable.hierarchyContains(newToken.lexeme) && !newToken.lexeme.equals("this")) {
                if (declaredCheck)
                    throw new SemanticException(newToken.lineNumber, "Identifier " + newToken.lexeme + " used without previously declaring.");
                else {

                    Symbol requireClassObject = (this.currentSubroutineTable.getInfo().getKind() == Symbol.Kind.METHOD || this.currentSubroutineTable.getInfo().getKind() == Symbol.Kind.CONSTRUCTOR) ?
                            this.currentSymbolTable.findHierarchySymbol("this") : null;

                    Identifier unresolvedIdentifier = new Identifier(
                            this.globalSymbolTable.getName(),
                            newToken.lexeme,
                            newToken.lineNumber,
                            null,
                            requireClassObject
                    );
                    this.unresolvedIdentifiers.add(unresolvedIdentifier);
                    return unresolvedIdentifier;
                }

            // SEMANTIC ANALYSIS - check to see if the symbol has been initialized
            } else if (initializedCheck && !this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).isInitialized())
                throw new SemanticException(newToken.lineNumber, "Identifier " + newToken.lexeme + " used before being initialized.");

            // we now know the identifier is in the symbol table so return it
            else {

                Symbol requireClassObject = (this.currentSubroutineTable.getInfo().getKind() == Symbol.Kind.METHOD || this.currentSubroutineTable.getInfo().getKind() == Symbol.Kind.CONSTRUCTOR) ?
                        this.currentSymbolTable.findHierarchySymbol("this") : null;

                return new Identifier(
                        this.globalSymbolTable.getName(),
                        newToken.lexeme,
                        newToken.lineNumber,
                        this.currentSymbolTable.findHierarchySymbol(newToken.lexeme),
                        requireClassObject
                );
            }
        }

        // Class identifier
        if (isClassIdentifier) {
            this.t.getNextToken();
            Token newScopedToken = this.t.getNextToken();

            // check to see if the class level identifier has been declared (it could be a variable or the name of a class)
            if (!this.currentSymbolTable.hierarchyContains(newToken.lexeme) && !newToken.lexeme.equals(this.globalSymbolTable.getName())) {
                if (declaredCheck)
                    throw new SemanticException(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                else {
                    Identifier unresolvedIdentifier = new Identifier(newToken.lexeme, newScopedToken.lexeme, newToken.lineNumber);
                    this.unresolvedIdentifiers.add(unresolvedIdentifier);
                    return unresolvedIdentifier;
                }

            // outer identifier (identifier that references the class) references scoped identifier that is part of the current class
            } else if (newToken.lexeme.equals(this.globalSymbolTable.getName())) {
                if (!this.globalSymbolTable.contains(newScopedToken.lexeme)) {
                    if (declaredCheck)
                        throw new SemanticException(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                    else {
                        Identifier unresolvedIdentifier = new Identifier(this.globalSymbolTable.getName(), newScopedToken.lexeme, newScopedToken.lineNumber);
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

            // outer identifier (identifier that references the class) references scoped identifier that is part of a class
            } else {
                String classType = this.currentSymbolTable.findHierarchySymbol(newToken.lexeme).getType();

                if (!this.globalSymbolTable.getName().equals(classType)) {
                    if (declaredCheck)
                        throw new SemanticException(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
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
                } else {
                    if (!this.globalSymbolTable.contains(newScopedToken.lexeme)) {
                        if (declaredCheck)
                            throw new SemanticException(newScopedToken.lineNumber, "Identifier " + newScopedToken.lexeme + " used without previously declaring.");
                        else {
                            Identifier unresolvedIdentifier = new Identifier(this.globalSymbolTable.getName(), newToken.lexeme, newToken.lineNumber);
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
        throw new ParserException("Something went wrong");
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
        Token identifier = this.t.getNextToken();
        Symbol symbol;

        // check for redeclaration
        if (this.currentSymbolTable.subroutineContains(identifier.lexeme))
            throw new SemanticException(this.t.peekNextToken().lineNumber, "Redeclaration of identifier: " + identifier.lexeme);

        symbol = this.currentSymbolTable.addSymbol(identifier.lexeme, type, Symbol.Kind.fromString(kind), isInitialized).getSymbol();

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

    private void parseConditionalStatment() throws IOException, ParserException, SemanticException {
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
    private boolean requiresThis;

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