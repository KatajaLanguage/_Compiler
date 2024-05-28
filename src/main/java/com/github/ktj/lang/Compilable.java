package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.compiler.Compiler;
import com.github.ktj.compiler.CompilerUtil;

import java.util.HashMap;

public abstract class Compilable {

    public final Modifier modifier;
    public final HashMap<String, String> uses;
    public final String file;
    public int line;

    public Compilable(Modifier modifier, HashMap<String, String> uses, String file, int line){
        this.modifier = modifier;
        this.uses = uses;
        this.file = file;
        this.line = line;
    }

    public String getType(String type){
        return uses.getOrDefault(type, null);
    }

    public abstract void validateTypes();

    protected String validateType(String type) throws RuntimeException{
        if(CompilerUtil.isPrimitive(type)) return type;

        if(!uses.containsKey(type) || !CompilerUtil.classExist(uses.get(type))) throw new RuntimeException(STR."Unknown Type \{type} at \{file}:\{line}");

        return uses.get(type);
    }

    public int getAccessFlag(){
        int accessFlag = switch (modifier.accessFlag){
            case ACC_PACKAGE_PRIVATE -> 0;
            case ACC_PUBLIC -> AccessFlag.PUBLIC;
            case ACC_PRIVATE -> AccessFlag.PRIVATE;
            case ACC_PROTECTED -> AccessFlag.PROTECTED;
        };

        if(modifier.finaly || modifier.constant) accessFlag += AccessFlag.FINAL;
        if(modifier.abstrakt) accessFlag += AccessFlag.ABSTRACT;
        if(modifier.statik) accessFlag += AccessFlag.STATIC;
        if(modifier.synchronised) accessFlag += AccessFlag.SYNCHRONIZED;

        return accessFlag;
    }
}