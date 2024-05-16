package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.HashMap;

public abstract class Compilable {

    protected final Modifier modifier;
    protected final HashMap<String, String> uses;

    public Compilable(Modifier modifier, HashMap<String, String> uses){
        this.modifier = modifier;
        this.uses = uses;
    }

    public String getType(String type){
        return uses.getOrDefault(type, null);
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
