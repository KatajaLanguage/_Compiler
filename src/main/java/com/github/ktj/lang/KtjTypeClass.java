package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.ArrayList;
import java.util.HashMap;

public final class KtjTypeClass extends Compilable{

    public final String[] values;

    public KtjTypeClass(Modifier modifier, String[] values, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, uses, statics, file, line);
        this.values = values;
    }

    public boolean hasValue(String value){
        for(String v:values) if(v.equals(value)) return true;

        return false;
    }

    public int ordinal(String value){
        int result = 0;

        do{
            if(values[result].equals(value)) return result;

            result++;
        }while(result < values.length);

        return -1;
    }

    @Override
    public void validateTypes() {

    }

    @Override
    public int getAccessFlag() {
        return super.getAccessFlag() + AccessFlag.ENUM + (modifier.finaly ? 0 : AccessFlag.FINAL);
    }
}
