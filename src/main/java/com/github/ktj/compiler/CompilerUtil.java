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

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.*;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CompilerUtil {

    public static final Set<String> PRIMITIVES = new HashSet<>();
    public static final Set<String> NUM_BOOL_OPERATORS = new HashSet<>();
    public static final Set<String> NUMBER_OPERATORS = new HashSet<>();

    static{
        PRIMITIVES.add("int");
        PRIMITIVES.add("double");
        PRIMITIVES.add("float");
        PRIMITIVES.add("short");
        PRIMITIVES.add("long");
        PRIMITIVES.add("boolean");
        PRIMITIVES.add("char");
        PRIMITIVES.add("byte");

        NUM_BOOL_OPERATORS.add("==");
        NUM_BOOL_OPERATORS.add("!=");
        NUM_BOOL_OPERATORS.add(">");
        NUM_BOOL_OPERATORS.add("<");
        NUM_BOOL_OPERATORS.add(">=");
        NUM_BOOL_OPERATORS.add("<=");

        NUMBER_OPERATORS.add("+");
        NUMBER_OPERATORS.add("-");
        NUMBER_OPERATORS.add("*");
        NUMBER_OPERATORS.add("/");
        NUMBER_OPERATORS.add("+=");
        NUMBER_OPERATORS.add("-=");
        NUMBER_OPERATORS.add("*=");
        NUMBER_OPERATORS.add("/=");
        NUMBER_OPERATORS.add("%=");
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
                    if(type.startsWith("[")) desc.append("[").append(toDesc(type.substring(1)));
                    else desc.append("L").append(type.replace(".", "/")).append(";");
                    break;
            }
        }

        return desc.toString();
    }

    public static String signatureToDesc(String methodSignature, String returnType){
        StringBuilder sb = new StringBuilder("(");
        if(!methodSignature.isEmpty()) for(String arg:methodSignature.split("%")) sb.append(toDesc(arg));
        sb.append(")").append(toDesc(returnType));
        return sb.toString();
    }

    public static boolean classExist(String name){
        if(name.startsWith("[")) return classExist(name.substring(1));

        if(PRIMITIVES.contains(name)) return true;

        try {
            Class.forName(name);
            return true;
        }catch(ClassNotFoundException ignored){}

        return Compiler.Instance().classes.containsKey(name);
    }

    public static boolean isInterface(String name){
        Compilable c = Compiler.Instance().classes.get(name);

        if(c != null){
            return c instanceof KtjInterface && !(c instanceof KtjClass);
        }else{
            try {
                return Class.forName(name).isInterface();
            }catch(ClassNotFoundException ignored){}
        }

        return false;
    }

    public static boolean isClass(String name){
        Compilable c = Compiler.Instance().classes.get(name);

        if(c != null){
            return c instanceof KtjClass;
        }else{
            try {
                return !Class.forName(name).isEnum() && !Class.forName(name).isInterface();
            }catch(ClassNotFoundException ignored){}
        }

        return false;
    }

    public static boolean isType(String name){
        Compilable c = Compiler.Instance().classes.get(name);

        if(c != null){
            return c instanceof KtjTypeClass;
        }else{
            try {
                return Class.forName(name).isEnum();
            }catch(ClassNotFoundException ignored){}
        }

        return false;
    }

    public static String[] getTypes(String name){
        Compilable c = Compiler.Instance().classes.get(name);

        if(c != null){
            assert c instanceof KtjTypeClass;
            return ((KtjTypeClass) c).values;
        }else{
            try{
                Class<?> clazz = Class.forName(name);
                assert clazz.isEnum();
                Enum<?>[] values = (Enum<?>[]) clazz.getEnumConstants();
                ArrayList<String> result = new ArrayList<>();
                for(Enum<?> e:values) result.add(e.name());
                return result.toArray(new String[0]);
            }catch(ClassNotFoundException ignored){}
        }

        return new String[0];
    }

    public static String[] getOperatorReturnType(String type, String operator){
        if(type.equals("boolean") && operator.equals("!")) return new String[]{"boolean"};
        if((operator.equals("++") || operator.equals("--")) && PRIMITIVES.contains(type) && !type.equals("boolean")) return new String[]{type};

        if(!PRIMITIVES.contains(type)){
            return getMethod(type, false, operatorToIdentifier(operator), type);
        }

        return null;
    }

    public static String[] getOperatorReturnType(String type1, String type2, String operator){
        if(type1.equals("null")) return null;

        if(operator.equals("=")) return type1.equals(type2) ?new String[]{type1, operator} : null;

        if(PRIMITIVES.contains(type1)){
            if(!type1.equals(type2))
                return null;

            if(type1.equals("boolean") && (operator.equals("&&") || operator.equals("||")))
                return new String[]{"boolean", operator};

            if((type1.equals("double") || type1.equals("float")) && (operator.equals("&") || operator.equals("|") || operator.equals("<<") || operator.equals(">>") || operator.equals("^"))) return null;

            if(NUM_BOOL_OPERATORS.contains(operator))
                return type1.equals("boolean") ? null : new String[]{"boolean", operator};

            if(NUMBER_OPERATORS.contains(operator))
                return type1.equals("boolean") ? null : new String[]{type1, operator};
        }else{
            if ((operator.equals("===") || operator.equals("!==")) && (type1.equals(type2) || type2.equals("null"))) return new String[]{"boolean", operator};
            if (operator.equals("+") && type1.equals("java.lang.String") && type2.equals("java.lang.String")) return new String[]{"java.lang.String", operator};
            return getMethod(type1, false, operatorToIdentifier(operator)+"%"+type2, type1);
        }

        return null;
    }

    public static String[] getMethod(String clazzName, boolean statik, String method, String callingClazz){
        String[] generics = clazzName.split("\\|").length == 2 ? clazzName.split("\\|")[1].split("%") : new String[0];
        clazzName = clazzName.split("\\|")[0];
        if(Compiler.Instance().classes.containsKey(clazzName)){
            Compilable compilable = Compiler.Instance().classes.get(clazzName);
            if(compilable instanceof KtjDataClass){
                if(!method.equals("<init>") || statik || method.split("%").length - 1 != ((KtjDataClass) compilable).fields.size()) return null;

                String[] parameters = method.split("%");
                KtjField[] fields = ((KtjDataClass) compilable).fields.values().toArray(new KtjField[0]);
                boolean matches = true;

                for (int i = 0; i < fields.length; i++){
                    if(!isSuperClass(parameters[i + 1], fields[i].type)){
                        matches = false;
                        break;
                    }
                }

                if(matches){
                    StringBuilder sb = new StringBuilder();
                    for(KtjField field:((KtjDataClass) compilable).fields.values()) sb.append(sb.length() == 0 ? "":"%").append(field.type);
                    return new String[]{clazzName, sb.toString()};
                }
            }else if(compilable instanceof KtjTypeClass){
                return getMethod("java.lang.Enum", statik, method, callingClazz);
            }else if(compilable instanceof KtjInterface){
                String methodName = method.split("%")[0];
                for(String mName:((KtjInterface) compilable).methods.keySet()){
                    if((mName.startsWith(methodName+"%") || mName.equals(methodName)) && (method.split("%").length - 1) == ((KtjInterface) compilable).methods.get(mName).parameter.length){
                        String[] parameters = method.split("%");
                        KtjMethod.Parameter[] parameter = ((KtjInterface) compilable).methods.get(mName).parameter;
                        boolean matches = true;

                        for (int i = 0; i < parameter.length; i++){
                            if(compilable.uses.containsKey(parameter[i].type)) {
                                if (!isSuperClass(parameters[i + 1], parameter[i].type)) {
                                    matches = false;
                                    break;
                                }
                            }else{
                                for(int j = 0;j < compilable.genericTypes.size();j++){
                                    if(parameter[i].type.equals(compilable.genericTypes.get(j).type) && !isSuperClass(parameters[i + 1], generics[j])){
                                        matches = false;
                                        break;
                                    }
                                }
                                if(!matches) break;
                            }
                        }

                        if(matches && canAccess(callingClazz, clazzName, ((KtjInterface) compilable).methods.get(mName).modifier.accessFlag) && (((KtjInterface) compilable).methods.get(mName).modifier.statik == statik)){
                            if(compilable.uses.containsKey(((KtjInterface) compilable).methods.get(mName).returnType) || mName.startsWith("<init>")) return new String[]{((KtjInterface) compilable).methods.get(mName).returnType, mName.contains("%") ? mName.split("%", 2)[1] : "", null};
                            else for(int j = 0;j < compilable.genericTypes.size();j++) if(((KtjInterface) compilable).methods.get(mName).returnType.equals(compilable.genericTypes.get(j).name)) return new String[]{generics[j], mName.contains("%") ? mName.split("%", 2)[1] : "", generics[j]};
                        }
                    }
                }

                if(compilable instanceof KtjClass) return getMethod(((KtjClass) compilable).superclass, statik, method, callingClazz);
            }
        }else{
            try{
                Class<?> clazz = Class.forName(clazzName);
                String methodName = method.split("%")[0];
                String[] parameters = method.split("%");
                if(methodName.equals("<init>")){
                    if(statik) return null;
                    for(Constructor<?> constructor:clazz.getConstructors()){
                        if(constructor.getParameterCount() == parameters.length - 1) {
                            boolean matches = true;
                            for (int i = 0; i < constructor.getParameterCount(); i++) {
                                if (!isSuperClass(parameters[i + 1], adjustType(constructor.getParameterTypes()[i].getTypeName()))) {
                                    matches = false;
                                    break;
                                }
                            }
                            if (matches && canAccess(callingClazz, clazzName, getAccessFlag(constructor.getModifiers()))) {
                                StringBuilder sb = new StringBuilder();
                                for(Class<?> field:constructor.getParameterTypes()) sb.append(sb.length() == 0 ? "":"%").append(adjustType(field.getTypeName()));
                                return new String[]{clazzName, sb.toString()};
                            }
                        }
                    }
                }else{
                    for(Method m:clazz.getMethods()){
                        if(m.getName().equals(methodName) && m.getParameterCount() == parameters.length - 1) {
                            boolean matches = true;
                            for (int i = 0; i < m.getParameterCount(); i++) {
                                if (!isSuperClass(parameters[i + 1], adjustType(m.getParameterTypes()[i].getTypeName()))) {
                                    matches = false;
                                    break;
                                }
                            }
                            if (matches && canAccess(callingClazz, clazzName, getAccessFlag(m.getModifiers())) && ((m.getModifiers() & AccessFlag.STATIC) != 0) == statik) {
                                StringBuilder sb = new StringBuilder();
                                for(int i = 0;i < m.getParameterCount();i++){
                                    sb.append(sb.length() == 0 ? "":"%").append(adjustType(m.getParameterTypes()[i].getTypeName()));
                                    if(m.getGenericParameterTypes()[i] instanceof TypeVariable<?>){
                                        TypeVariable<?>[] typeParameters = Class.forName(clazzName).getTypeParameters();
                                        for(int j = 0; j < typeParameters.length; j++) if(typeParameters[j].getName().equals(((TypeVariable<?>) m.getGenericParameterTypes()[i]).getName()) && !generics[j].equals(parameters[i + 1])) return null;
                                    }
                                }

                                if(m.getGenericReturnType() instanceof TypeVariable<?>){
                                    TypeVariable<?>[] typeParameters = Class.forName(clazzName).getTypeParameters();
                                    for(int i = 0; i < typeParameters.length; i++) if(typeParameters[i].getName().equals(((TypeVariable<?>) m.getGenericReturnType()).getName())) return new String[]{generics[i], sb.toString(), generics[i]};
                                }else return new String[]{m.getReturnType().getName(), sb.toString()};
                            }
                        }
                    }
                }

                if(!clazzName.equals("java.lang.Object")) return getMethod(clazz.getSuperclass().getName(), statik, method, callingClazz);
            }catch(ClassNotFoundException ignored){}
        }
        return null;
    }

    private static String adjustType(String type){
        if(!type.contains("[")) return type;

        StringBuilder result = new StringBuilder(type.split("\\[")[0]);
        for(int i=0;i<type.split("\\[").length - 1;i++)  result.insert(0, "[");
        return result.toString();
    }

    public static int getEnumOrdinal(String clazz, String value){
        if(Compiler.Instance().classes.containsKey(clazz) && Compiler.Instance().classes.get(clazz) instanceof KtjTypeClass){
            KtjTypeClass c = (KtjTypeClass) Compiler.Instance().classes.get(clazz);
            return c.ordinal(value);
        }
        try{
            Class<?> c = Class.forName(clazz);
            if(c.isEnum()){
                Object[] enumConstants = c.getEnumConstants();
                for(Object enumConstant:enumConstants){
                    if(enumConstant.toString().equals(value)) return ((Enum<?>) enumConstant).ordinal();
                }
            }
        }catch(ClassNotFoundException ignored){}
        return -1;
    }

    public static String[] getFieldType(String clazzName, String field, boolean statik, String callingClazz){
        String[] generics = clazzName.split("\\|").length == 2 ? clazzName.split("\\|")[1].split("%") : new String[0];
        clazzName = clazzName.split("\\|")[0];

        if(clazzName.startsWith("[")){
            if(!field.equals("length")) return null;
            return new String[]{"int", null};
        }

        Compilable compilable = Compiler.Instance().classes.get(clazzName);

        if(compilable != null) {
            if(compilable instanceof KtjClass){
                if (((KtjClass)(compilable)).fields.containsKey(field) && ((KtjClass)(compilable)).fields.get(field).modifier.statik == statik) {
                    if(!canAccess(callingClazz, clazzName, ((KtjClass) (compilable)).fields.get(field).modifier.accessFlag)) return null;
                    String type = ((KtjClass) (compilable)).fields.get(field).type;
                    int gi = compilable.genericIndex(type);
                    if(gi == -1) return new String[]{type, null};
                    if(generics.length == 0) return new String[]{compilable.correctType(type), null};
                    return new String[]{compilable.genericTypes.get(gi).type, generics[gi]};
                }
            }else if(compilable instanceof KtjTypeClass){
                if (((KtjTypeClass)(compilable)).hasValue(field) && statik) return new String[]{clazzName, null};
            }else if(compilable instanceof KtjDataClass){
                if (((KtjDataClass)(compilable)).fields.containsKey(field) && ((KtjDataClass)(compilable)).fields.get(field).modifier.statik == statik)
                    return new String[]{((KtjDataClass)(compilable)).fields.get(field).type, null};
            }
        }else{
            try{
                Field f = Class.forName(clazzName).getField(field);
                if((((f.getModifiers() & AccessFlag.STATIC) != 0) == statik) && canAccess(callingClazz, clazzName, getAccessFlag(f.getModifiers()))){
                    if(f.getGenericType() instanceof TypeVariable<?>){
                        TypeVariable<?>[] typeParameters = Class.forName(clazzName).getTypeParameters();
                        for(int i = 0; i < typeParameters.length; i++) if(typeParameters[i].getName().equals(((TypeVariable<?>) f.getGenericType()).getName())) return new String[]{f.getType().getName(), generics[i]};
                    }else return new String[]{f.getType().getName(), null};
                }
            }catch(Exception ignored){}
        }

        return null;
    }

    private static boolean canAccess(String type1, String type2, AccessFlag flag){
        if(flag == AccessFlag.ACC_PUBLIC) return true;
        if(flag == AccessFlag.ACC_PRIVATE) return type1.equals(type2);
        if(flag == AccessFlag.ACC_PROTECTED) return isSuperClass(type1, type2);

        String pakage1 = type1.contains(".") ? type1.substring(0, type1.lastIndexOf('.')) : "";
        String pakage2 = type2.contains(".") ? type2.substring(0, type2.lastIndexOf('.')) : "";
        return pakage1.equals(pakage2);
    }

    private static AccessFlag getAccessFlag(int flag){
        if((flag & AccessFlag.PUBLIC) != 0) return AccessFlag.ACC_PUBLIC;
        if((flag & AccessFlag.PRIVATE) != 0) return AccessFlag.ACC_PRIVATE;
        if((flag & AccessFlag.PROTECTED) != 0) return AccessFlag.ACC_PROTECTED;
        else return AccessFlag.ACC_PACKAGE_PRIVATE;
    }

    public static boolean canAccess(String type1, String type2){
        if(Compiler.Instance().classes.containsKey(type2)) return canAccess(type1, type2, Compiler.Instance().classes.get(type2).modifier.accessFlag);
        try{
            return canAccess(type1, type2, getAccessFlag(Class.forName(type2).getModifiers()));
        }catch(ClassNotFoundException e){
            throw new RuntimeException("unable to find "+type2);
        }
    }

    public static boolean validateGenericTypes(String clazzName, String...types){
        if(types.length == 0) return true;

        Compilable c = Compiler.Instance().classes.get(clazzName);
        if(c != null){
            if(c instanceof KtjDataClass){
                return false;
            }else if(c instanceof KtjInterface){
                if(types.length != c.genericTypes.size()) return false;
                for(int i = 0;i < types.length;i++) if(!isSuperClass(types[i], c.genericTypes.get(i).type)) return false;
                return true;
            }
        }else{
            try{
                Class<?> clazz = Class.forName(clazzName);
                TypeVariable<?>[] typeParameters = clazz.getTypeParameters();
                if(typeParameters.length != types.length) return false;
                for(TypeVariable<?> type:typeParameters) for(Type bound:type.getBounds()) if(!isSuperClass(clazzName, bound.getTypeName())) return false;
                return true;
            }catch(ClassNotFoundException ignored){
                return false;
            }
        }

        return false;
    }

    public static boolean isFinal(String clazz){
        Compilable compilable = Compiler.Instance().classes.get(clazz);

        if(compilable != null){
            return compilable.modifier.finaly;
        }else{
            try{
                Class<?> c = Class.forName(clazz);
                return (c.getModifiers() & AccessFlag.FINAL) != 0;
            }catch(ClassNotFoundException ignored){}
        }

        return false;
    }

    public static boolean isSuperClass(String clazz, String superClass){
        if(clazz.equals(superClass)) return true;
        if(PRIMITIVES.contains(clazz) || PRIMITIVES.contains(superClass)) return false;
        if(clazz.equals("java.lang.Object")) return false;
        if(superClass.equals("java.lang.Object")) return true;

        if(Compiler.Instance().classes.containsKey(clazz)){
            Compilable c = Compiler.Instance().classes.get(clazz);

            if(c instanceof KtjInterface && !(c instanceof KtjClass)) return false;
            else if(c instanceof KtjTypeClass) return superClass.equals("java.lang.Enum");
            else if(c instanceof KtjClass){
                if(((KtjClass) c).superclass != null && ((KtjClass) c).superclass.equals(superClass)) return true;
                for(String i:((KtjClass) c).interfaces) if(i.equals(superClass)) return true;

                return ((KtjClass) c).superclass != null && isSuperClass(((KtjClass) c).superclass, superClass);
            }
        }else{
            try{
                Class<?> c = Class.forName(clazz);
                if(c.getSuperclass().toString().split(" ")[1].equals(superClass)) return true;
                for(Class<?> i:c.getInterfaces()) if(i.toString().split(" ")[1].equals(superClass)) return true;

                return isSuperClass(clazz, c.getSuperclass().toString().split(" ")[1]);
            }catch(ClassNotFoundException ignored){}
        }
        return false;
    }

    public static boolean canCast(String type, String to){
        if(PRIMITIVES.contains(type) && PRIMITIVES.contains(to)) return !type.equals("boolean") && !to.equals("boolean");

        if(type.startsWith("[")){
            if(!to.startsWith("[")) return false;
            return canCast(type.substring(1), to.substring(1));
        }

        return isSuperClass(type, to) || isSuperClass(to, type);
    }

    static AST.Return getDefaultReturn(String type){
        AST.Return ast = new AST.Return();
        ast.type = type;

        if(!type.equals("void")) {
            ast.calc = new AST.Calc();
            ast.calc.type = type;

            AST.Value value = new AST.Value();

            value.type = type;
            Token.Type t;
            String v;

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

    public static String getDefaultValue(String type){
        switch(type){
            case "int":
                return "0i";
            case "short":
                return "0s";
            case "long":
                return "0l";
            case "double":
                return "0.0d";
            case "float":
                return "0.0f";
            case "char":
                return "' '";
            case "boolean":
                return "false";
            case "java.lang.String":
                return "\"\"";
            default:
                return "null";
        }
    }
}
