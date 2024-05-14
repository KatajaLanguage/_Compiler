package com.github.ktj.lang;

public class KtjMethod extends Compilable{

    private final String returnType;
    private final String code;

    public KtjMethod(Modifier modifier, String returnType, String code) {
        super(modifier);
        this.returnType = returnType;
        this.code = code;
    }

}
