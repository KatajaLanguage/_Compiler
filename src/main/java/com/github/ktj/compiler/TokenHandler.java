package com.github.ktj.compiler;

import java.util.Arrays;

final class TokenHandler{

    private final Token[][] token;
    private int line;
    private int i;

    TokenHandler(Token[][] token){
        this.token = token;
        line = 0;
        i = -1;
    }

    public Token next() throws RuntimeException{
        if(i + 1 < token[line].length){
            i++;
            return token[line][i];
        }else{
            while(line++ < token.length){
                if(token[line].length > 0){
                    i = 0;
                    return token[line][i];
                }
            }
        }
        throw new RuntimeException("Expected Token got nothing");
    }

    public Token current() throws RuntimeException{
        if(i == -1 || i >= token[line].length) throw new RuntimeException("Expected Token got nothing");
        return token[line][i];
    }

    public Token last() throws RuntimeException{
        if(i > 0){
            i--;
            return token[line][i];
        }else{
            while(line-- >= 0){
                if(token[line].length > 0){
                    i = token[line].length - 1;
                    return token[line][i];
                }
            }
        }
        throw new RuntimeException("Expected Token got nothing");
    }

    public void assertToken(String...strings) throws RuntimeException{
        Token t = next();

        for(String string:strings) if(t.equals(string)) return;

        throw new RuntimeException("Expected one of "+Arrays.toString(strings)+" got "+t.s);
    }

    public void assertToken(Token.Type...types) throws RuntimeException{
        Token t = next();

        for(Token.Type type:types) if(t.equals(type)) return;

        throw new RuntimeException("Expected one of "+Arrays.toString(types)+" got "+t.t);
    }

    public void assertEndOfStatement() throws RuntimeException{
        if(i + 1 != token[line].length) assertToken(";");
    }

    public boolean hasNext(){
        if(i + 1 < token[line].length) return true;
        else{
            while(line++ < token.length){
                if(token[line].length > 0){
                    i = -1;
                    return true;
                }
            }
        }
        return false;
    }

    public String getIndex(){
        return line +":"+i;
    }

    public void setIndex(String index){
        String[] s = index.split(":");
        line = Integer.parseInt(s[0]);
        i = Integer.parseInt(s[1]);
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();

        for(Token[] line:token){
            for(Token token:line) sb.append(token).append(" ");
            sb.append("\n");
        }

        return sb.toString();
    }
}
