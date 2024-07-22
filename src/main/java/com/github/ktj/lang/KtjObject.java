package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjObject extends KtjClass{
    public KtjObject(Modifier modifier, HashMap<String, String> uses, ArrayList<String> statics, String file, int line) {
        super(modifier, null, uses, statics, file, line);
    }

    @Override
    public void validateInit() {
        boolean initExist = false;

        for(String method: methods.keySet()) {
            if(method.startsWith("<init>")) {
                initExist = true;
                break;
            }
        }

        if(!initExist){
            methods.put("<init>", new KtjMethod(new Modifier(AccessFlag.ACC_PRIVATE), "void", "", new KtjMethod.Parameter[0], uses, statics, file, Integer.MIN_VALUE));
        }
    }
}
