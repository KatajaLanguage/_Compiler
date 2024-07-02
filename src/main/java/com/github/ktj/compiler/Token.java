package com.github.ktj.compiler;

class Token{

    public enum Type{
        SIMPLE, IDENTIFIER, STRING, OPERATOR, CHAR, INTEGER, LONG, DOUBLE, FLOAT, SHORT;

        @Override
        public String toString() {
            if(this == FLOAT) return "float";
            if(this == DOUBLE) return "double";
            if(this == SHORT) return "short";
            if(this == INTEGER) return "int";
            if(this == LONG) return "long";
            if(this == CHAR) return "char";
            if(this == STRING) return "java.lang.String";
            return super.toString();
        }

        public static Type value(String name){
            if(name.equals("float")) return FLOAT;
            if(name.equals("double")) return DOUBLE;
            if(name.equals("short")) return SHORT;
            if(name.equals("int")) return INTEGER;
            if(name.equals("long")) return LONG;
            if(name.equals("char")) return CHAR;
            if(name.equals("java.lang.String")) return STRING;
            return null;
        }
    }

    public String s;
    public Type t;

    public Token(String s, Type t){
        this.s = s;
        this.t = t;
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
