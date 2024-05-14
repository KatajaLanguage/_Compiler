package com.github.x.lang;

import com.github.x.bytecode.AccessFlag;

public abstract class Compilable {

    protected final Modifier modifier;

    public Compilable(Modifier modifier){
        this.modifier = modifier;
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
