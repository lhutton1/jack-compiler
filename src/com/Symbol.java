package com;

public class Symbol {
    private String name;
    private String type;
    private String kind;
    private int index;

    public Symbol(String name, String type, String kind, int index) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.index = index;
    }

    public String getName() { return this.name; }
    public String getType() { return this.type; }
    public String getKind() { return this.kind; }
    public int getIndex() { return this.index; }

    @Override
    public String toString() {
        return "<Symbol " + name + ", " + type + ", " + kind + ", " + index + ">";
    }
}
