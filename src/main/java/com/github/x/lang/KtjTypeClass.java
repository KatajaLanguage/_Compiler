package com.github.x.lang;

import com.github.x.bytecode.AccessFlag;

public final class KtjTypeClass extends Compilable{

    public final String[] values;

    public KtjTypeClass(Modifier modifier, String[] values) {
        super(modifier);
        this.values = values;
    }

    public boolean hasValue(String value){
        for(String v:values) if(v.equals(value)) return true;

        return false;
    }

    @Override
    public int getAccessFlag() {
        return super.getAccessFlag() + AccessFlag.ENUM + (modifier.finaly ? 0 : AccessFlag.FINAL);
    }
}
