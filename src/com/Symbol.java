package com;

public class Symbol {
    private String name;
    private String type;
    private Kind kind;
    private SymbolTable childSymbolTable;
    private int index;
    private boolean initialized;

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

        Kind(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

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
     * Create a new SYMBOL.
     * @param name name of the SYMBOL.
     * @param type type that the SYMBOL stores/returns.
     * @param kind the kind of SYMBOL.
     * @param index the index that the SYMBOL is placed at.
     * @param initialized whether the SYMBOL is initialized
     * @param childSymbolTable the child SYMBOL table if it's kind is class or subroutine.
     */
    public Symbol(String name, String type, Kind kind, int index, boolean initialized, SymbolTable childSymbolTable) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.index = index;
        this.initialized = initialized;
        this.childSymbolTable = childSymbolTable;
    }

    public Symbol(String name, String type, Kind kind, int index, boolean initialized) {
        this(name, type, kind, index, initialized,null);
    }

    public String getName() { return this.name; }
    public String getType() { return this.type; }
    public Kind getKind() { return this.kind; }
    public SymbolTable getChildSymbolTable() { return this.childSymbolTable; }
    public int getIndex() { return this.index; }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public String toString() {
        return "<Symbol " + name + ", " + type + ", " + kind + ", " + index + ", " + childSymbolTable + ", " + initialized +">";
    }
}
