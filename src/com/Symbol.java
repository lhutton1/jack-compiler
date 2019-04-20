package com;

/**
 * A symbol is used to represent an identifier that is part of the symbol table.
 * It will be declared in the symbol table in which it belongs.
 */
public class Symbol {
    private String name;                    // The name of the symbol.
    private String type;                    // The type that the symbol represents.
    private Kind kind;                      // The kind that the symbol represents i.e. static, field, etc.
    private int index;                      // The index that the symbol is located at - for vm code.

    private SymbolTable childSymbolTable;   // If the symbol is a subroutine of if/while statement it will have a child symbol table.
    private boolean initialized;            // Whether or not the symbol has been initialized, for semantic analysis.


    /**
     * The kind of the symbol represents how the
     * identifier was declared in the first place.
     */
    public enum Kind {
        STATIC("static"),
        FIELD("this"),
        LOCAL("local"),
        ARGUMENT("argument"),
        CLASS("class"),
        INNER("inner"),
        METHOD("method"),
        FUNCTION("function"),
        CONSTRUCTOR("constructor"),
        POINTER("pointer");

        private String name;

        /**
         * Associate the argument with the name field.
         * @param name the name assigned to enum value.
         */
        Kind(String name) { this.name = name; }

        /**
         * Get the name assigned to the enum.
         * @return the name.
         */
        public String getName() { return name; }

        /**
         * Convert a string to the relevant enum.
         * @param text the text to convert to enum.
         * @return the kind that the text represents.
         */
        public static Kind fromString(String text) {
            for (Kind kind : Kind.values()) {
                if (kind.getName().equals(text))
                    return kind;
            }
            throw new IllegalArgumentException("String " + text + " not found in enum");
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    /**
     * Create a new symbol.
     * @param name name of the symbol.
     * @param type type that the symbol stores/returns.
     * @param kind the kind of symbol.
     * @param index the index that the symbol is placed at.
     * @param initialized whether the symbol is initialized.
     * @param childSymbolTable the child symbol table if it's kind is class or subroutine.
     */
    public Symbol(String name, String type, Kind kind, int index, boolean initialized, SymbolTable childSymbolTable) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.index = index;
        this.initialized = initialized;
        this.childSymbolTable = childSymbolTable;
    }

    /**
     * Create a new symbol that doesnt have a child symbol table
     * @param name name of the symbol.
     * @param type type that the symbol stores/returns.
     * @param kind the kind of symbol.
     * @param index the index that the symbol is placed at.
     * @param initialized whether the symbol is initialized.
     */
    public Symbol(String name, String type, Kind kind, int index, boolean initialized) {
        this(name, type, kind, index, initialized,null);
    }

    /**
     * Getters.
     */
    public String getName() { return this.name; }
    public String getType() { return this.type; }
    public Kind getKind() { return this.kind; }
    public SymbolTable getChildSymbolTable() { return this.childSymbolTable; }
    public int getIndex() { return this.index; }

    public boolean isInitialized() { return this.initialized; }

    /**
     * Setters.
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public String toString() {
        return "<Symbol " + name + ", " + type + ", " + kind + ", " + index + ", " + childSymbolTable + ", " + initialized +">";
    }
}
