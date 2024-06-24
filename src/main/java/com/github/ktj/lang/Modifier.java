package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

public final class Modifier {

    public final AccessFlag accessFlag;
    public boolean finaly = false;
    public boolean constant = false;
    public boolean abstrakt = false;
    public boolean synchronised = false;
    public boolean statik = false;

    public Modifier(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public boolean isValidForField(){
        return !(finaly || abstrakt);
    }

    public boolean isValidForMethod(){
        return !(finaly || constant) && !(accessFlag == AccessFlag.ACC_PRIVATE && abstrakt);
    }

    public boolean isValidForInit(){
        return !(finaly || constant || abstrakt || synchronised || statik);
    }

    public boolean isValidForType(){
        return !(abstrakt || synchronised || statik);
    }

    public boolean isValidForData(){
        return !(abstrakt || synchronised || statik);
    }

    public boolean isValidForInterface(){
        return !(constant || finaly || synchronised || statik);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Modifier)) return false;

        if(((Modifier) obj).accessFlag != accessFlag) return false;
        if(((Modifier) obj).finaly == finaly) return true;
        if(((Modifier) obj).constant == constant) return true;
        if(((Modifier) obj).abstrakt == abstrakt) return true;
        if(((Modifier) obj).synchronised == synchronised) return true;
        if(((Modifier) obj).statik == statik) return true;

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        switch(accessFlag){
            case ACC_PACKAGE_PRIVATE:
                sb.append(0);
                break;
            case ACC_PUBLIC:
                sb.append(1);
                break;
            case ACC_PROTECTED:
                sb.append(2);
                break;
            case ACC_PRIVATE:
                sb.append(3);
                break;
        }

        sb.append(finaly ? 't' : 'f');
        sb.append(constant ? 't' : 'f');
        sb.append(abstrakt ? 't' : 'f');
        sb.append(synchronised ? 't' : 'f');
        sb.append(statik ? 't' : 'f');

        return sb.toString();
    }

    public static Modifier of(String s){
        Modifier mod;

        char[] string = s.toCharArray();
        switch(string[0]){
            case '0':
                mod = new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);
                break;
            case '1':
                mod = new Modifier(AccessFlag.ACC_PUBLIC);
                break;
            case '2':
                mod = new Modifier(AccessFlag.ACC_PROTECTED);
                break;
            default:
                mod = new Modifier(AccessFlag.ACC_PRIVATE);
                break;
        }

        mod.finaly = string[1] == 't';
        mod.constant = string[2] == 't';
        mod.abstrakt = string[3] == 't';
        mod.synchronised = string[4] == 't';
        mod.statik = string[5] == 't';

        return mod;
    }

    public static Modifier of(int modifier){
        Modifier mod;

        if((modifier & AccessFlag.PRIVATE) != 0) mod = new Modifier(AccessFlag.ACC_PRIVATE);
        else if((modifier & AccessFlag.PUBLIC) != 0) mod = new Modifier(AccessFlag.ACC_PUBLIC);
        else if((modifier & AccessFlag.PROTECTED) != 0) mod = new Modifier(AccessFlag.ACC_PROTECTED);
        else mod = new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);

        mod.finaly = (modifier & AccessFlag.FINAL) != 0;
        mod.constant = (modifier & AccessFlag.FINAL) != 0;
        mod.abstrakt = (modifier & AccessFlag.ABSTRACT) != 0;
        mod.synchronised = (modifier & AccessFlag.SYNCHRONIZED) != 0;
        mod.statik = (modifier & AccessFlag.STATIC) != 0;

        return mod;
    }
}
