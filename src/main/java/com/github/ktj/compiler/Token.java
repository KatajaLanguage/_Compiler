/*
 * Copyright (C) 2024 Xaver Weste
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
