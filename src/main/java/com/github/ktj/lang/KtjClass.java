package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.compiler.CompilerUtil;

import java.util.HashMap;

public class KtjClass extends KtjInterface{

    public HashMap<String, KtjField> fields;
    public String superclass = null;

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

        for(String field: fields.keySet()) if(fields.get(field).initValue != null && fields.get(field).modifier.statik) sb.append(field).append(" = ").append(fields.get(field).initValue).append("\n");

        return sb.length() == 0 ? null : sb.toString();
    }

    public String initValues(){
        StringBuilder sb = new StringBuilder();

        for(String field: fields.keySet()) if(fields.get(field).initValue != null && !fields.get(field).modifier.statik) sb.append(field).append(" = ").append(fields.get(field).initValue).append("\n");

        return sb.length() == 0 ? null : sb.toString();
    }

    public void validateInit(){
        boolean initExist = false;

        for(String method: methods.keySet()) {
            if(method.startsWith("<init>")) {
                initExist = true;
                break;
            }
        }

        if(!initExist){
            methods.put("<init>", new KtjMethod(new Modifier(AccessFlag.ACC_PUBLIC), "void", "", new KtjMethod.Parameter[0], uses, file, Integer.MIN_VALUE));
        }
    }

    @Override
    public void validateTypes() {
        super.validateTypes();

        for(KtjField field:fields.values()) field.validateTypes();

        if(superclass != null){
            superclass = super.validateType(superclass);
            if(CompilerUtil.isFinal(superclass)) throw new RuntimeException("Class "+superclass+" is final");
        }
    }

    public int getAccessFlag(){
        return super.getAccessFlag() - AccessFlag.INTERFACE - AccessFlag.ABSTRACT;
    }
}
