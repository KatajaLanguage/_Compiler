package com.github.ktj.compiler;

import java.util.Set;

public class CompilerUtil {

    public static final String[] PRIMITIVES = new String[]{"int", "double", "float", "short", "long", "boolean", "char", "byte"};
    public static final Set<String> BOOL_OPERATORS = Set.of("==", "!=", "||", "&&", ">", "<", ">=", "<=");
    public static final Set<String> NUMBER_OPERATORS = Set.of("+", "-", "*", "/");

    public static String operatorToIdentifier(String operator){
        return switch (operator){
            case "==" -> "equals";
            case "&&" -> "and";
            case "||" -> "or";
            default -> {
                StringBuilder result = new StringBuilder();

                for(char c:operator.toCharArray()){
                    result.append(switch(c){
                        case '=' -> "equals";
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

                yield result.toString();
            }
        };
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
                default        -> desc.append("L").append(type).append(";");
            }
        }

        return desc.toString();
    }

    public static boolean classExist(String name){
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

    public static boolean isPrimitive(String type){
        for(String p:PRIMITIVES) if(p.equals(type)) return true;

        return false;
    }
}
