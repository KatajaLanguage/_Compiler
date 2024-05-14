package com.github.x.lang;

import com.github.x.bytecode.AccessFlag;

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

    public boolean isValidForType(){
        return !(abstrakt || synchronised || statik);
    }

    public boolean isValidForData(){
        return !(abstrakt || synchronised || statik);
    }
}
