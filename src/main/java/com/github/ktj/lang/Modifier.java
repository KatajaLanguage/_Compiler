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

    public Modifier(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public boolean isValidForField(){
        return !(finaly || abstrakt || strict);
    }

    public boolean isValidForMethod(){
        return !(finaly || constant || volatil || transint) && !(accessFlag == AccessFlag.ACC_PRIVATE && abstrakt);
    }

    public boolean isValidForInit(){
        return !(finaly || constant || abstrakt || synchronised || statik || volatil || transint || strict);
    }

    public boolean isValidForObject(){
        return !(abstrakt || synchronised || statik || constant || volatil || transint || strict);
    }

    public boolean isValidForType(){
        return !(abstrakt || synchronised || statik || volatil || transint || strict);
    }

    public boolean isValidForData(){
        return !(abstrakt || synchronised || statik || volatil || transint || strict);
    }

    public boolean isValidForInterface(){
        return !(constant || finaly || synchronised || statik || volatil || transint || strict);
    }

    public boolean isValidForClass(){
        return !(constant || synchronised || statik || volatil || transint || strict);
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
}
