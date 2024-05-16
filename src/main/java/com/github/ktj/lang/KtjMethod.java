package com.github.ktj.lang;

import java.util.HashMap;

public class KtjMethod extends Compilable{

    private final String returnType;
    private final String code;

    public KtjMethod(Modifier modifier, String returnType, String code, HashMap<String, String> uses) {
        super(modifier, uses);
        this.returnType = returnType;
        this.code = code;
    }

}
