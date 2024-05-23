package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.HashMap;

public final class KtjTypeClass extends Compilable{

    public final String[] values;

    public KtjTypeClass(Modifier modifier, String[] values, HashMap<String, String> uses, String file, int line){
        super(modifier, uses, file, line);
        this.values = values;
    }

    public boolean hasValue(String value){
        for(String v:values) if(v.equals(value)) return true;

        return false;
    }

    @Override
    public void validateTypes() {

    }

    @Override
    public int getAccessFlag() {
        return super.getAccessFlag() + AccessFlag.ENUM + (modifier.finaly ? 0 : AccessFlag.FINAL);
    }
}
