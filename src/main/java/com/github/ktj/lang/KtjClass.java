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
import com.github.ktj.compiler.Compiler;
import com.github.ktj.compiler.CompilerUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class KtjClass extends KtjInterface{

    public HashMap<String, KtjField> fields;
    public String[] interfaces = new String[0];
    public String superclass = null;

    public KtjClass(Modifier modifier, ArrayList<GenericType> genericTypes, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, genericTypes, uses, statics, file, line);
        fields = new HashMap<>();
    }

    public boolean addField(String name, KtjField field){
        if(fields.containsKey(name)) return true;

        fields.put(name, field);
        return false;
    }

    public boolean isEmpty(){
        return fields.isEmpty() && methods.isEmpty();
    }

    public String initValues(){
        StringBuilder sb = new StringBuilder();

        for(String field: fields.keySet()) if(fields.get(field).initValue != null && !fields.get(field).modifier.statik) sb.append(field).append(" = ").append(fields.get(field).initValue).append(";");

        return sb.toString();
    }

    public String clinitValues(){
        StringBuilder sb = new StringBuilder();

        for(String field: fields.keySet()) if(fields.get(field).initValue != null && fields.get(field).modifier.statik) sb.append(field).append(" = ").append(fields.get(field).initValue).append(";");

        return sb.toString();
    }

    public void validateInit(String className){
        boolean initExist = false;

        for(String method: methods.keySet()) {
            if(method.startsWith("<init>")) {
                initExist = true;
                break;
            }
        }

        if(!initExist){
            methods.put("<init>", new KtjMethod(new Modifier(AccessFlag.ACC_PUBLIC), genericTypes, className, "", new KtjMethod.Parameter[0], uses, statics, file, Integer.MIN_VALUE));
        }
    }

    public void validateClinit(String className){
        boolean clinitExist = false;

        for(String method: methods.keySet()) {
            if(method.startsWith("<clinit>")) {
                clinitExist = true;
                break;
            }
        }

        if(!clinitExist){
            Modifier mod = new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);
            mod.statik = true;
            methods.put("<clinit>", new KtjMethod(mod, genericTypes, className, "", new KtjMethod.Parameter[0], uses, statics, file, Integer.MIN_VALUE));
        }
    }

    public void validateInterfaces(){
        for(String interfaceName:interfaces){
            if(!CompilerUtil.isInterface(interfaceName)) throw new RuntimeException("Expected "+interfaceName+" to be a interface");
            if(Compiler.Instance().classes.containsKey(interfaceName)) validateKtjInterface((KtjInterface) Compiler.Instance().classes.get(interfaceName));
            else validateJavaInterface(interfaceName);
        }
    }

    private void validateKtjInterface(KtjInterface i){
        for(String method:i.methods.keySet()){
            if(!methods.containsKey(method)) throw new RuntimeException("No implementation for "+method+" found");

            Modifier mod1 = methods.get(method).modifier;
            Modifier mod2 = i.methods.get(method).modifier;

            if(mod1.accessFlag != mod2.accessFlag) throw new RuntimeException("Expected "+method+" to be "+mod2.accessFlag);
            if(mod1.synchronised) throw new RuntimeException("Expected "+method+" to be synchronised");
        }
    }

    private void validateJavaInterface(String interfaceName){
        try{
            Class<?> clazz = Class.forName(interfaceName);

            for(Method method:clazz.getDeclaredMethods()){
                StringBuilder desc = new StringBuilder(method.getName());

                for(Class<?> p:method.getParameterTypes()) desc.append("%").append(p.toString().split(" ")[1]);

                if(!methods.containsKey(desc.toString())) throw new RuntimeException("No implementation for "+desc+" found");
            }
        }catch(ClassNotFoundException ignored){}
    }

    @Override
    public void validateTypes() {
        super.validateTypes();

        for(KtjField field:fields.values()){
            field.validateTypes();
            if(field.initValue == null) field.initValue = CompilerUtil.getDefaultValue(field.type);
        }

        Arrays.stream(interfaces).forEach(type -> validateType(type, true));

        if(superclass != null){
            superclass = super.validateType(superclass, true);
            if(CompilerUtil.isFinal(superclass)) throw new RuntimeException("Class "+superclass+" is final");

            if(CompilerUtil.isInterface(superclass)){
                String[] help = new String[interfaces.length + 1];
                help[0] = superclass;
                System.arraycopy(interfaces, 0, help, 1, interfaces.length);

                interfaces = help;
                superclass = "java.lang.Object";
            }
        }else superclass = "java.lang.Object";

        for(KtjMethod method: methods.values()) if(method instanceof KtjConstructor) ((KtjConstructor) method).validate(superclass);
    }

    public int getAccessFlag(){
        return super.getAccessFlag() - AccessFlag.INTERFACE - AccessFlag.ABSTRACT;
    }
}
