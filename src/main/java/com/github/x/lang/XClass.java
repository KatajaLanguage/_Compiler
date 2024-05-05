package com.github.x.lang;

import com.github.x.bytecode.AccessFlag;

public class XClass extends Compilable{

    private final boolean finaly;
    private final boolean abstrakt;

    public XClass(AccessFlag accessFlag, boolean finaly, boolean abstrakt) {
        super(accessFlag);
        this.finaly = finaly;
        this.abstrakt = abstrakt;
    }
}
