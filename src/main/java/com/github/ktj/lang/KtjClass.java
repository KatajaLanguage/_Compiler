package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.HashMap;

public class KtjClass extends KtjInterface{

    public HashMap<String, KtjField> fields;

    public KtjClass(Modifier modifier, HashMap<String, String> uses, String file, int line){
        super(modifier, uses, file, line);
        fields = new HashMap<>();
    }

    public boolean addField(String name, KtjField field){
        if(fields.containsKey(name)) return true;

        fields.put(name, field);
        return false;
    }

    public boolean isEmpty(){
        return fields.isEmpty() && methods.isEmpty();
    }

    @Override
    public void validateTypes() {
        super.validateTypes();

        for(KtjField field:fields.values()) field.validateTypes();
    }

    public int getAccessFlag(){
        return super.getAccessFlag() - AccessFlag.INTERFACE;
    }
}
