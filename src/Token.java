public class Token {
    private enum TokenTypes {
        keyword,
        id,
        assignop,
        addop,
        mulop,
        num
    }

    public String lexeme;
    public TokenTypes type;
}
