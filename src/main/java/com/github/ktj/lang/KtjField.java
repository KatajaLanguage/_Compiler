package com.github.ktj.lang;

public class KtjField extends Compilable{

    public final String type;

    public KtjField(Modifier modifier, String type) {
        super(modifier);
        this.type = type;
    }
}
