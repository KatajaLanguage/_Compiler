package com.github.x.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class TokenHandler {

    private final Token[] tokens;
    private int index;

    public TokenHandler(List<Token> tokens){
        this.tokens = tokens.toArray(new Token[0]);
        index = -1;
    }

    public Token next() throws RuntimeException {
        if(!hasNext()) throw new RuntimeException(STR."expected Token got nothing in: \{this}");
        index++;
        return tokens[index];
    }

    public Token current() throws RuntimeException {
        if(!isValid()) throw new RuntimeException(STR."expected Token got nothing in: \{this}");
        return tokens[index];
    }

    public Token last() throws RuntimeException {
        index--;
        return index >= 0 ? tokens[index] : null;
    }

    public boolean hasNext(){
        return index + 1 < tokens.length;
    }

    public boolean isValid(){
        return index < tokens.length && index > -1;
    }

    public boolean isEmpty(){
        return tokens.length == 0;
    }

    public int length(){
        return tokens.length;
    }

    public void toFirst(){
        index = -1;
    }

    public TokenHandler getInBracket() throws RuntimeException {
        if(!isValid()) throw new RuntimeException(STR."expected left bracket got nothing in: \{this}");
        Token current = tokens[index];
        if(!Set.of("(", "[", "{").contains(current.s())) throw new RuntimeException(STR."expected left bracket got \{tokens[index].s()} in: \{this}");

        String openingBracket = current.s();
        String closingBracket = openingBracket.equals("(") ? ")" : openingBracket.equals("[") ? "]" : "}";
        List<Token> tokenList = new ArrayList<>();
        int i = 1;

        while (i > 0 && hasNext()){
            current = next();
            if(current.s().equals(closingBracket)) i--;
            else if(current.s().equals(openingBracket)) i++;
            if(i > 0) tokenList.add(current);
        }

        if(i > 0) throw new RuntimeException(STR."expected right bracket got nothing in: \{this}");
        return new TokenHandler(tokenList);
    }

    public Token assertToken(String string) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException(STR."expected \{string} got nothing in: \{this}");
        Token token = next();
        if(!token.s().equals(string)) throw new RuntimeException(STR."expected \{string} got \{token.s()} in: \{this}");
        return token;
    }

    public Token assertToken(String...strings) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException(STR."expected one of \{arrayToString(strings)} got nothing in: \{this}");
        Token token = next();

        for(String str:strings) if(token.s().equals(str)) return token;

        throw new RuntimeException(STR."expected one of \{arrayToString(strings)} got \{token.s()} in: \{this}");
    }

    public Token assertToken(Token.Type type) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException(STR."expected \{type.toString()} got nothing in: \{this}");
        Token token = next();
        if(token.t() != type) throw new RuntimeException(STR."expected \{type.toString()} got \{token.t().toString()} in: \{this}");
        return token;
    }

    public Token assertToken(Token.Type...types) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException(STR."expected one of \{arrayToString(types)} got nothing in: \{this}");
        Token token = next();

        for(Token.Type t:types) if(token.t() == t) return token;

        throw new RuntimeException(STR."expected one of \{arrayToString(types)} got \{token.t().toString()} in: \{this}");
    }

    public Token assertToken(String string, Token.Type...types){
        if(!hasNext()) throw new RuntimeException(STR."expected one of \{arrayToString(types)} got nothing in: \{this}");
        Token token = next();

        if(token.equals(string))
            return token;

        for(Token.Type t:types)
            if(token.t() == t)
                return token;

        throw new RuntimeException(STR."expected one of \{string}, \{arrayToString(types)} got \{token.t().toString()} in: \{this}");
    }

    public static Token assertToken(Token token, String string) throws RuntimeException {
        if(!token.equals(string)) throw new RuntimeException(STR."expected \{string} got \{token.s()}");
        return token;
    }

    public static Token assertToken(Token token, Token.Type type) throws RuntimeException {
        if(token.t() != type) throw new RuntimeException(STR."expected \{type.toString()} got \{token.t().toString()}");
        return token;
    }

    public void assertHasNext() throws RuntimeException{
        if(index >= tokens.length) throw new RuntimeException(STR."expected Token, got Nothing in: \{this}");
    }

    public void assertNull() throws RuntimeException{
        if(hasNext()) throw new RuntimeException(STR."expected nothing, got \{tokens[index].t().toString()} in: \{this}");
    }

    private String arrayToString(String[] sa){
        StringBuilder sb = new StringBuilder();

        for(String s:sa) sb.append(s).append(", ");

        sb.deleteCharAt(sb.length() - 2);
        return sb.toString();
    }

    private String arrayToString(Token.Type[] ta){
        StringBuilder sb = new StringBuilder();

        for(Token.Type t:ta) sb.append(t.toString()).append(", ");

        sb.deleteCharAt(sb.length() - 2);
        return sb.toString();
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        for(int i = 0;i < tokens.length;i++)
            if(i == index)
                sb.append(" |> ").append(tokens[i]).append(" <|  ");
            else
                sb.append(tokens[i]).append(" ");
        return sb.toString();
    }

    public String toStringNonMarked(){
        StringBuilder sb = new StringBuilder();
        for(Token t:tokens) sb.append(t.s()).append(" ");
        return sb.toString();
    }
}
