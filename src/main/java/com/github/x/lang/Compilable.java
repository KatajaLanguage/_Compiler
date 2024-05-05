package com.github.x.lang;

import com.github.x.bytecode.AccessFlag;

public abstract class Compilable {

    protected final AccessFlag accessFlag;

    public Compilable(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public int getAccessFlag(){
        return switch(accessFlag){
            case ACC_PUBLIC -> AccessFlag.PUBLIC;
            case ACC_PRIVATE -> AccessFlag.PRIVATE;
            case ACC_PROTECTED -> AccessFlag.PROTECTED;
            default -> 0;
        };
    }
}
