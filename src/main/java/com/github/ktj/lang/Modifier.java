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

    public boolean isValidForObject(){
        return !(abstrakt || synchronised || statik || constant);
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
}
