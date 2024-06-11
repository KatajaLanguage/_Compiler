package com.github.ktj.compiler;

import com.github.ktj.lang.*;

import java.util.Set;

public class CompilerUtil {

    public static final String[] PRIMITIVES = new String[]{"int", "double", "float", "short", "long", "boolean", "char", "byte"};
    public static final Set<String> BOOL_OPERATORS = Set.of("==", "!=", "||", "&&", ">", "<", ">=", "<=");
    public static final Set<String> NUMBER_OPERATORS = Set.of("+", "-", "*", "/");

    public static String operatorToIdentifier(String operator){
        if(operator.equals("==")) return "equals";

        StringBuilder result = new StringBuilder();

        for(char c:operator.toCharArray()){
            result.append(switch(c){
                case '=' -> "equal";
                case '+' -> "add";
                case '-' -> "subtract";
                case '*' -> "multiply";
                case '/' -> "divide";
                case '<' -> "lessThan";
                case '>' -> "greaterThan";
                case '!' -> "not";
                case '%' -> "mod";
                case '&' -> "and";
                case '|' -> "or";
                case '^' -> "exponentiation";
                case '~' -> "proportional";
                default -> throw new RuntimeException("internal Compiler error");
            });
        }

        return result.toString();
    }

    public static String validateClassName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        return name;
    }

    public static String toDesc(String...types){
        StringBuilder desc = new StringBuilder();

        for(String type:types){
            switch (type){
                case "int"     -> desc.append("I");
                case "short"   -> desc.append("S");
                case "long"    -> desc.append("J");
                case "double"  -> desc.append("D");
                case "float"   -> desc.append("F");
                case "boolean" -> desc.append("Z");
                case "char"    -> desc.append("C");
                case "byte"    -> desc.append("B");
                case "void"    -> desc.append("V");
                default        -> {
                    if(type.startsWith("[")) desc.append(STR."[\{toDesc(type.substring(1))}");
                    else desc.append("L").append(type).append(";");
                }
            }
        }

        return desc.toString();
    }

    public static boolean classExist(String name){
        if(name.startsWith("[")) return classExist(name.substring(1));

        if(isPrimitive(name)) return true;

        try {
            Class.forName(name);
            return true;
        }catch(ClassNotFoundException ignored){}

        return Compiler.Instance().classes.containsKey(name);
    }

    public static String getOperatorReturnType(String type1, String type2, String operator){
        if(isPrimitive(type1)){
            if(!type1.equals(type2))
                return null;

            if(BOOL_OPERATORS.contains(operator))
                return "boolean";

            if(NUMBER_OPERATORS.contains(operator))
                return type1.equals("boolean") ? null : type1;
        }
        return null;
    }

    public static String getFieldType(String clazzName, String field, boolean statik){
        Compilable compilable = Compiler.Instance().classes.get(clazzName);

        if(compilable == null) return null;

        switch(compilable){
            case KtjClass clazz -> {
                if (clazz.fields.containsKey(field) && clazz.fields.get(field).modifier.statik != statik)
                    return clazz.fields.get(field).type;
            }
            case KtjTypeClass clazz -> {
                if (clazz.hasValue(field) && statik) return clazzName;
            }
            case KtjDataClass clazz -> {
                if (clazz.fields.containsKey(field) && clazz.fields.get(field).modifier.statik != statik) return clazz.fields.get(field).type;
            }
            default -> {}
        }

        return null;
    }

    public static boolean isFinal(String clazzName, String field){
        Compilable compilable = Compiler.Instance().classes.get(clazzName);

        if(compilable == null) return false;

        switch(compilable){
            case KtjClass clazz -> {
                if (clazz.fields.containsKey(field))
                    return clazz.fields.get(field).modifier.finaly;
            }
            case KtjTypeClass clazz -> {
                return true;
            }
            case KtjDataClass clazz -> {
                if (clazz.fields.containsKey(field)) return clazz.fields.get(field).modifier.finaly;
            }
            default -> {}
        }

        return false;
    }

    public static boolean isPrimitive(String type){
        for(String p:PRIMITIVES) if(p.equals(type)) return true;

        return false;
    }

    public static AST.Return getDefaultReturn(String type){
        AST.Return ast = new AST.Return();
        ast.type = type;

        if(!type.equals("void")) {
            ast.calc = new AST.Calc();
            ast.calc.type = type;

            AST.Value value = new AST.Value();

            value.type = type;
            value.token = new Token(switch (type) {
                case "char" -> " ";
                case "int", "short", "long", "byte" -> "0";
                case "float", "double" -> "0.0";
                case "boolean" -> "false";
                default -> "null";
            }, switch (type) {
                case "float" -> Token.Type.FLOAT;
                case "double" -> Token.Type.DOUBLE;
                case "int", "short", "byte" -> Token.Type.INTEGER;
                case "long" -> Token.Type.LONG;
                case "char" -> Token.Type.CHAR;
                default -> Token.Type.IDENTIFIER;
            });

            ast.calc.value = value;
        }

        return ast;
    }
}
