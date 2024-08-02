package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

public final class Modifier {

    public final AccessFlag accessFlag;
    public boolean finaly = false;
    public boolean constant = false;
    public boolean abstrakt = false;
    public boolean synchronised = false;
    public boolean statik = false;
    public boolean volatil = false;
    public boolean transint = false;
    public boolean strict = false;
    public boolean natife = false;

    public Modifier(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public boolean isValidForField(){
        return !(finaly || abstrakt || strict || natife);
    }

    public boolean isValidForMethod(){
        return !(finaly || constant || volatil || transint) && !(accessFlag == AccessFlag.ACC_PRIVATE && abstrakt);
    }

    public boolean isValidForInit(){
        return !(finaly || constant || abstrakt || synchronised || volatil || transint || strict|| natife);
    }

    public boolean isValidForObject(){
        return !(abstrakt || synchronised || statik || constant || volatil || transint || strict|| natife);
    }

    public boolean isValidForType(){
        return !(abstrakt || synchronised || statik || volatil || transint || strict|| natife);
    }

    public boolean isValidForData(){
        return !(abstrakt || synchronised || statik || volatil || transint || strict|| natife);
    }

    public boolean isValidForInterface(){
        return !(constant || finaly || synchronised || statik || volatil || transint || strict|| natife);
    }

    public boolean isValidForClass(){
        return !(constant || synchronised || statik || volatil || transint || strict|| natife);
    }

    public static Modifier ofInt(int mod){
        Modifier result = new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);
        if((mod & AccessFlag.PUBLIC) != 0) result = new Modifier(AccessFlag.ACC_PUBLIC);
        else if((mod & AccessFlag.PROTECTED) != 0) result = new Modifier(AccessFlag.ACC_PROTECTED);
        else if((mod & AccessFlag.PRIVATE) != 0) result = new Modifier(AccessFlag.ACC_PRIVATE);

        if((mod & AccessFlag.FINAL) != 0){
            result.finaly = true;
            result.constant = true;
        }
        if((mod & AccessFlag.ABSTRACT) != 0) result.abstrakt = true;
        if((mod & AccessFlag.SYNCHRONIZED) != 0) result.synchronised = true;
        if((mod & AccessFlag.STATIC) != 0) result.statik = true;
        if((mod & AccessFlag.VOLATILE) != 0) result.volatil = true;
        if((mod & AccessFlag.TRANSIENT) != 0) result.transint = true;
        if((mod & AccessFlag.STRICT) != 0) result.strict = true;
        if((mod & AccessFlag.NATIVE) != 0) result.natife = true;

        return result;
    }
}
