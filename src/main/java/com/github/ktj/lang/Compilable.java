/*
 * Copyright (C) 2024 Xaver Weste
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.compiler.CompilerUtil;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class Compilable {

    public final Modifier modifier;
    public final ArrayList<GenericType> genericTypes;
    public final HashMap<String, String> uses;
    public final ArrayList<String> statics;
    public final String file;
    public int line;

    public Compilable(Modifier modifier, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        this.modifier = modifier;
        this.genericTypes = null;
        this.uses = uses;
        this.statics = statics;
        this.file = file;
        this.line = line;
    }

    public Compilable(Modifier modifier, ArrayList<GenericType> genericTypes, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        this.modifier = modifier;
        this.genericTypes = genericTypes;
        this.uses = uses;
        this.statics = statics;
        this.file = file;
        this.line = line;
    }

    public abstract void validateTypes();

    public void validateUses(String type){
        for(String clazz: uses.values()){
            if(!CompilerUtil.classExist(clazz)) throw new RuntimeException("Unable to find "+clazz);
            if(!CompilerUtil.canAccess(type, clazz)) throw new RuntimeException("Class "+clazz+" is outside of scope");
        }
        for(String clazz: statics) if(!CompilerUtil.isClass(uses.get(clazz))) throw new RuntimeException("Can't static use "+clazz);
    }

    public String validateType(String type, boolean errorAt) throws RuntimeException{
        if(type.startsWith("[")) return "["+validateType(type.substring(1), errorAt);

        if(CompilerUtil.PRIMITIVES.contains(type)) return type;

        if(!uses.containsKey(type)){
            if(genericTypes != null) for(GenericType gType:genericTypes) if (gType.name.equals(type)) return type;
            throw new RuntimeException("Unknown Type "+type+(errorAt?" at "+file+":"+line:""));
        }

        return uses.get(type);
    }

    public int genericIndex(String type){
        if(genericTypes == null) return -1;

        for(int i = 0;i < genericTypes.size();i++) if(genericTypes.get(i).name.equals(type)) return i;

        return -1;
    }

    public String correctType(String type){
        int i = genericIndex(type);
        if(i == -1) return type;
        assert genericTypes != null;
        return genericTypes.get(i).type;
    }

    public int getAccessFlag(){
        int accessFlag = 0;
        switch (modifier.accessFlag){
            case ACC_PUBLIC:
                accessFlag = AccessFlag.PUBLIC;
                break;
            case ACC_PRIVATE:
                accessFlag = AccessFlag.PRIVATE;
                break;
            case ACC_PROTECTED:
                accessFlag = AccessFlag.PROTECTED;
                break;
        }

        if(modifier.finaly || modifier.constant) accessFlag += AccessFlag.FINAL;
        if(modifier.abstrakt) accessFlag += AccessFlag.ABSTRACT;
        if(modifier.statik) accessFlag += AccessFlag.STATIC;
        if(modifier.synchronised) accessFlag += AccessFlag.SYNCHRONIZED;
        if(modifier.volatil) accessFlag += AccessFlag.VOLATILE;
        if(modifier.transint) accessFlag += AccessFlag.TRANSIENT;
        if(modifier.strict) accessFlag += AccessFlag.STRICT;
        if(modifier.natife) accessFlag += AccessFlag.NATIVE;

        return accessFlag;
    }
}
