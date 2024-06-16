package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class CompilerUtil {

    public static final String[] PRIMITIVES = new String[]{"int", "double", "float", "short", "long", "boolean", "char", "byte"};
    public static final Set<String> BOOL_OPERATORS = new HashSet<>();
    public static final Set<String> NUM_BOOL_OPERATORS = new HashSet<>();
    public static final Set<String> NUMBER_OPERATORS = new HashSet<>();

    static{
        BOOL_OPERATORS.add("==");
        BOOL_OPERATORS.add("!=");
        NUMBER_OPERATORS.add("||");
        NUMBER_OPERATORS.add("&&");
        NUMBER_OPERATORS.add(">");
        NUMBER_OPERATORS.add("<");
        NUMBER_OPERATORS.add(">=");
        NUMBER_OPERATORS.add("<=");
        NUMBER_OPERATORS.add("+");
        NUMBER_OPERATORS.add("-");
        NUMBER_OPERATORS.add("*");
        NUMBER_OPERATORS.add("/");
    }

    public static String operatorToIdentifier(String operator){
        if(operator.equals("==")) return "equals";

        StringBuilder result = new StringBuilder();

        for(char c:operator.toCharArray()){
            if(c == '=') result.append("equal");
            else if(c == '+') result.append("add");
            else if(c == '-') result.append("subtract");
            else if(c == '*') result.append("multiply");
            else if(c == '/') result.append("divide");
            else if(c == '<') result.append("lessThan");
            else if(c == '>') result.append("greaterThan");
            else if(c == '!') result.append("not");
            else if(c == '%') result.append("mod");
            else if(c == '&') result.append("and");
            else if(c == '|') result.append("or");
            else if(c == '^') result.append("exponentiation");
            else if(c == '~') result.append("proportional");
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
                case "int":
                    desc.append("I");
                    break;
                case "short":
                    desc.append("S");
                    break;
                case "long":
                    desc.append("J");
                    break;
                case "double":
                    desc.append("D");
                    break;
                case "float":
                    desc.append("F");
                    break;
                case "boolean":
                    desc.append("Z");
                    break;
                case "char":
                    desc.append("C");
                    break;
                case "byte":
                    desc.append("B");
                    break;
                case "void":
                    desc.append("V");
                    break;
                default:
                    if(type.startsWith("[")) desc.append("["+toDesc(type.substring(1)));
                    else desc.append("L").append(type.replace(".", "/")).append(";");
                    break;
            }
        }

        return desc.toString();
    }

    public static String toDesc(String returnType, AST.Calc[] args){
        StringBuilder sb = new StringBuilder("(");

        for(AST.Calc calc:args) sb.append(toDesc(calc.type));

        sb.append(")").append(toDesc(returnType));
        return sb.toString();
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

            if(NUM_BOOL_OPERATORS.contains(operator))
                return type1.equals("boolean") ? null : "boolean";

            if(NUMBER_OPERATORS.contains(operator))
                return type1.equals("boolean") ? null : type1;
        }else return ((operator.equals("==") || operator.equals("!=")) && type1.equals(type2)) ? "boolean" : null;

        return null;
    }

    public static String getMethodReturnType(String clazzName, String method, boolean statik, boolean allowPrivate){
        if(Compiler.Instance().classes.containsKey(clazzName)){
            return (Compiler.Instance().classes.get(clazzName) instanceof KtjInterface && ((KtjInterface)(Compiler.Instance().classes.get(clazzName))).methods.containsKey(method) && ((KtjInterface)(Compiler.Instance().classes.get(clazzName))).methods.get(method).modifier.statik == statik && (allowPrivate || ((KtjInterface)(Compiler.Instance().classes.get(clazzName))).methods.get(method).modifier.accessFlag != AccessFlag.ACC_PRIVATE)) ? ((KtjInterface)(Compiler.Instance().classes.get(clazzName))).methods.get(method).returnType : null;
        }else{
            try{
                if(method.startsWith("<init>")){
                    for (Constructor<?> m : Class.forName(clazzName).getConstructors()) {
                        if(method.split("%").length - 1 == m.getParameterTypes().length){
                            boolean matches = true;
                            for (int i = 0; i < m.getParameterTypes().length; i++) {
                                if (!method.split("%")[i + 1].equals(m.getParameterTypes()[i].toString().split(" ")[1])) {
                                    matches = false;
                                    break;
                                }
                            }
                            if(matches) return clazzName;
                        }
                    }
                }else {
                    for (Method m : Class.forName(clazzName).getMethods()) {
                        if(((m.getModifiers() & AccessFlag.STATIC) != 0 && !statik) || ((m.getModifiers() & AccessFlag.STATIC) == 0 && statik)) return null;
                        if (m.getName().equals(method.split("%")[0]) && method.split("%").length - 1 == m.getParameterTypes().length) {
                            boolean matches = true;
                            for (int i = 0; i < m.getParameterTypes().length; i++) {
                                if (!method.split("%")[i + 1].equals(m.getParameterTypes()[i].getName())){
                                    matches = false;
                                    break;
                                }
                            }
                            if (matches)
                                return m.getReturnType().toString().contains(" ") ? m.getReturnType().toString().split(" ")[1] : m.getReturnType().toString();
                        }
                    }
                }
            }catch(ClassNotFoundException ignored){}
        }
        return null;
    }

    public static String getFieldType(String clazzName, String field, boolean statik, boolean allowPrivate){
        if(clazzName.startsWith("[")){
            if(!field.equals("length")) return null;
            return "int";
        }

        Compilable compilable = Compiler.Instance().classes.get(clazzName);

        if(compilable != null) {
            if(compilable instanceof KtjClass){
                if (((KtjClass)(compilable)).fields.containsKey(field) && ((KtjClass)(compilable)).fields.get(field).modifier.statik == statik && (allowPrivate || ((KtjClass)(compilable)).fields.get(field).modifier.accessFlag != AccessFlag.ACC_PRIVATE))
                    return ((KtjClass)(compilable)).fields.get(field).type;
            }else if(compilable instanceof KtjTypeClass){
                if (((KtjTypeClass)(compilable)).hasValue(field) && statik) return clazzName;
            }else if(compilable instanceof KtjDataClass){
                if (((KtjDataClass)(compilable)).fields.containsKey(field) && ((KtjDataClass)(compilable)).fields.get(field).modifier.statik != statik)
                    return ((KtjDataClass)(compilable)).fields.get(field).type;
            }
        }else{
            try{
                Field f = Class.forName(clazzName).getField(field);
                if(((f.getModifiers() & AccessFlag.STATIC) != 0 && !statik) || ((f.getModifiers() & AccessFlag.STATIC) == 0 && statik) || ((f.getModifiers() & AccessFlag.PRIVATE) != 0 && !allowPrivate)) return null;
                return f.getType().toString().split(" ")[1];
            }catch(Exception ignored){}
        }

        return null;
    }

    public static boolean isFinal(String clazzName, String field){
        if(clazzName.startsWith("[")){
            return true;
        }

        Compilable compilable = Compiler.Instance().classes.get(clazzName);

        if(compilable != null) {
            if(compilable instanceof KtjTypeClass) return true;
            else if(compilable instanceof KtjDataClass){
                if (((KtjDataClass) (compilable)).fields.containsKey(field)) return ((KtjDataClass) (compilable)).fields.get(field).modifier.finaly;
            }else if(compilable instanceof KtjClass){
                if (((KtjClass) (compilable)).fields.containsKey(field))
                    return ((KtjClass) (compilable)).fields.get(field).modifier.finaly;
            }
        }else{
            try{
                Field f = Class.forName(clazzName).getField(field);
                return (f.getModifiers() & AccessFlag.FINAL) != 0;
            }catch(Exception ignored){}
        }

        return false;
    }

    public static boolean canCast(String type, String to){
        if(isPrimitive(type) && isPrimitive(to)) return true;

        return true;
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
            Token.Type t = null;
            String v = null;

            switch (type){
                case "char":
                    t = Token.Type.CHAR;
                    v = " ";
                    break;
                case "int":
                case "short":
                case "byte":
                    t = Token.Type.INTEGER;
                    v = "0";
                    break;
                case "long":
                    t = Token.Type.LONG;
                    v = "0";
                    break;
                case "float":
                    t = Token.Type.FLOAT;
                    v = "0.0";
                    break;
                case "double":
                    t = Token.Type.DOUBLE;
                    v = "0.0";
                    break;
                case "boolean":
                    t = Token.Type.IDENTIFIER;
                    v = "false";
                    break;
                default:
                    t = Token.Type.IDENTIFIER;
                    v = "null";
                    break;
            }

            value.token = new Token(v, t);

            ast.calc.arg = value;
        }

        return ast;
    }
}
