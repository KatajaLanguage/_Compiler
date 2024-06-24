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

    public String validateType(String type, boolean errorAt) throws RuntimeException{
        if(type.startsWith("[")) return "["+validateType(type.substring(1), errorAt);

        if(CompilerUtil.isPrimitive(type)) return type;

        if(!uses.containsKey(type) || !CompilerUtil.classExist(uses.get(type))) throw new RuntimeException("Unknown Type "+type+(errorAt?" at "+file+":"+line:""));

        return uses.get(type);
    }

    public int getAccessFlag(){
        int accessFlag = 0;
        switch (modifier.accessFlag){
            case ACC_PUBLIC:
                accessFlag = AccessFlag.PUBLIC;
                break;
            case ACC_PRIVATE:
                accessFlag = AccessFlag.PRIVATE;
                break;
            case ACC_PROTECTED:
                accessFlag = AccessFlag.PROTECTED;
                break;
        };

        if(modifier.finaly || modifier.constant) accessFlag += AccessFlag.FINAL;
        if(modifier.abstrakt) accessFlag += AccessFlag.ABSTRACT;
        if(modifier.statik) accessFlag += AccessFlag.STATIC;
        if(modifier.synchronised) accessFlag += AccessFlag.SYNCHRONIZED;

        return accessFlag;
    }
}
