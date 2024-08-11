package com.github.ktj.lang;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjConstructor extends KtjMethod{

    public String superCall;

    public KtjConstructor(Modifier modifier, ArrayList<GenericType> generics, String returnType, String superCall, String code, Parameter[] parameter, HashMap<String, String> uses, ArrayList<String> statics, String file, int line) {
        super(modifier, generics, returnType, code, parameter, uses, statics, file, line);
        this.superCall = superCall;
    }

    public void validate(String superClass){
        if(!superCall.isEmpty()) {
            String type = validateType(superCall.split("\\(")[0], true);
            if (!type.equals(superClass))
                throw new RuntimeException("expected type " + superClass + " got " + type + " at " + file + ":" + line);
        }
    }
}
