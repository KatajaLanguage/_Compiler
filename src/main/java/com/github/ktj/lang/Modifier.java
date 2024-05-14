package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

public final class Modifier {

    public final AccessFlag accessFlag;
    public boolean finaly = false;
    public boolean constant = false;
    public boolean abstrakt = false;
    public boolean synchronised = false;
    public boolean statik = false;

    public Modifier(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public boolean isValidForMethod(){
        return !(finaly || constant);
    }

    public boolean isValidForType(){
        return !(abstrakt || synchronised || statik);
    }

    public boolean isValidForData(){
        return !(abstrakt || synchronised || statik);
    }

    public boolean isValidForInterface(){
        return !(constant || finaly || synchronised || statik);
    }
}
