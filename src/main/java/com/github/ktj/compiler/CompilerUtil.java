package com.github.ktj.compiler;

public class CompilerUtil {

    public static final String[] PRIMITIVES = new String[]{"int", "double", "float", "short", "long", "boolean", "char", "byte"};

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

    public static boolean isPrimitive(String type){
        for(String p:PRIMITIVES) if(p.equals(type)) return true;

        return false;
    }
}
