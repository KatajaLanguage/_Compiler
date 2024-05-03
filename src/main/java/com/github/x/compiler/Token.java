package com.github.x.compiler;

public record Token(String s, Type t){

    public enum Type{
        SIMPLE, IDENTIFIER, STRING, OPERATOR, CHAR, INTEGER, LONG, DOUBLE, FLOAT, SHORT;

        @Override
        public String toString() {
            return switch (this){
                case FLOAT -> "float";
                case DOUBLE -> "double";
                case SHORT -> "short";
                case INTEGER -> "int";
                case LONG -> "long";
                case CHAR -> "char";
                case STRING -> "java.lang.String";
                default -> super.toString();
            };
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Token && ((Token) obj).t == t && ((Token) obj).equals(s);
    }

    public boolean equals(Token token){
        return token.equals(s);
    }

    public boolean equals(String s){
        return this.s.equals(s);
    }

    public boolean equals(Type t){
        return this.t == t;
    }

    @Override
    public String toString() {
        return s;
    }
}
