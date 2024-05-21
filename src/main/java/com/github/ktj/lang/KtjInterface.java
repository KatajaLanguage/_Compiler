package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.HashMap;

public class KtjInterface extends Compilable{

    public HashMap<String, KtjMethod> methods;

    public KtjInterface(Modifier modifier, HashMap<String, String> uses) {
        super(modifier, uses);
        methods = new HashMap<>();
    }

    public boolean addMethod(String desc, KtjMethod method){
        if(methods.containsKey(desc)) return true;

        methods.put(desc, method);
        return false;
    }

    @Override
    public void validateTypes(){
        HashMap<String, KtjMethod> help = new HashMap<>();

        for(String methodName:methods.keySet()){
            KtjMethod method = methods.get(methodName);
            method.validateTypes();

            String[] args = methodName.split("%");
            StringBuilder desc = new StringBuilder(args[0]);

            for(int i = 1;i < args.length;i++) desc.append("%").append(validateType(args[i]));

            help.put(desc.toString(), method);
        }

        methods = help;
    }

    public int getAccessFlag(){
        return super.getAccessFlag() + AccessFlag.INTERFACE;
    }
}
