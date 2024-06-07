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

    public String createClinit(){
        StringBuilder sb = new StringBuilder();

        for(String field: fields.keySet()) if(fields.get(field).initValue != null) sb.append(field).append(" = ").append(fields.get(field).initValue).append("\n");

        return sb.isEmpty() ? null : sb.toString();
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
