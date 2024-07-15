package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

final class Parser {

    private HashMap<String, Compilable> classes;
    private HashMap<String, String> uses;
    private ArrayList<String> statics;
    private Scanner sc;
    private TokenHandler th;
    private String path;
    private String name;
    private Compilable current = null;
    private KtjClass statik;
    private int line;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException("File "+file.getPath()+" did not exist");

        try{
            if(0 < file.getPath().length() - file.getName().length() - 1) path = file.getPath().substring(0, file.getPath().length() - file.getName().length() - 1);
            else path = "";
            name = file.getName().substring(0, file.getName().length() - 4);
            classes = new HashMap<>();
            uses = new HashMap<>();
            statics = new ArrayList<>();
            sc = new Scanner(file);
            statik = new KtjClass(new Modifier(AccessFlag.ACC_PUBLIC), uses, statics, getFileName(), -1);
            line = 0;
        }catch(FileNotFoundException ignored){
            throw new IllegalArgumentException("Unable to find "+file.getPath());
        }catch(IndexOutOfBoundsException ignored){
            throw new IllegalArgumentException("Illegal argument " + file.getPath());
        }

        uses.put("Object", "java.lang.Object");
        uses.put("String", "java.lang.String");
        statik.superclass = "Object";

        while(sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                try {
                    switch (th.next().s) {
                        case "use":
                            parseUse();
                        case "#":
                            break;
                        case "main":
                            parseMain();
                            break;
                        default:
                            parseModifier(null);
                            break;
                    }
                }catch(RuntimeException e){
                    if(e.getMessage().contains(" at ")) throw e;
                    else{
                        try {
                            err(e.getMessage());
                        }catch(RuntimeException err){
                            err.setStackTrace(e.getStackTrace());
                            throw err;
                        }
                    }
                }
            }
        }

        if(!statik.isEmpty()) classes.put(classes.isEmpty() ? name : "_"+name, statik);

        if(classes.size() == 1 && name.equals(classes.keySet().toArray(new String[0])[0])){
            String name = classes.keySet().toArray(new String[0])[0];
            Compilable clazz = classes.remove(name);
            classes.put(CompilerUtil.validateClassName((path.isEmpty() ? "" : path+".")+name), clazz);

            if(uses.containsKey(name)) err(name+" is already defined");
            uses.put(name, CompilerUtil.validateClassName((path.isEmpty() ? "" : path+".")+name));
        }else{
            HashMap<String, Compilable> help = new HashMap<>();

            for(String clazz: classes.keySet()){
                help.put(CompilerUtil.validateClassName((path.isEmpty() ? "" : path+".")+name+"."+clazz), classes.get(clazz));

                path = CompilerUtil.validateClassName(path);
                if(uses.containsKey(clazz)) err(clazz+" is already defined");
                uses.put(clazz, (path.isEmpty() ? "" : path+".")+name+"."+clazz);
            }

            classes = help;
        }

        return classes;
    }

    private void parseUse(){
        String current = th.assertToken(Token.Type.IDENTIFIER).s;

        if(th.assertToken("/", "from", ",").equals("/")){
            StringBuilder path = new StringBuilder(current);
            th.assertHasNext();
            th.last();

            while (th.hasNext()){
                if(th.assertToken("/", "as").equals("as")){
                    addUse(th.assertToken(Token.Type.IDENTIFIER).s, path.toString());
                    th.assertNull();
                    return;
                }else{
                    current = th.assertToken(Token.Type.IDENTIFIER).s;
                    path.append("/").append(current);
                }
            }

            addUse(current, path.toString().replace("/", "."));
        }else{
            ArrayList<String> classes = new ArrayList<>();
            classes.add(current);

            while(th.current().equals(",")){
                classes.add(th.assertToken(Token.Type.IDENTIFIER).s);
                th.assertToken(",", "from");
            }

            StringBuilder path = new StringBuilder(th.assertToken(Token.Type.IDENTIFIER).s);

            while (th.hasNext()){
                if(th.assertToken("/", "as").equals("as")){
                    if(classes.size() != 1) err("illegal argument");
                    addUse(th.assertToken(Token.Type.IDENTIFIER).s, path.toString().replace("/", ".")+"."+classes.get(0));
                }else path.append("/").append(th.assertToken(Token.Type.IDENTIFIER).s);
            }

            for(String clazz:classes) addUse(clazz, path.toString().replace("/", ".")+"."+clazz);
        }
    }

    private void addUse(String name, String path){
        if(uses.containsKey(name)) err(name+" is already defined");
        uses.put(name, path);
    }

    private void parseMain(){
        if(th.isNext("->")){
            StringBuilder code = new StringBuilder();

            while(th.hasNext()) code.append(" ").append(th.next());

            Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
            mod.statik = true;
            addMethod("main%[String", new KtjMethod(mod, "void", code.toString(), new KtjMethod.Parameter[]{new KtjMethod.Parameter(false, "[String", "args")}, uses, statics, getFileName(), line));
            return;
        }

        th.assertToken("{");
        th.assertNull();

        int _line = line;

        StringBuilder code = new StringBuilder();
        int i = 1;

        while(i != 0){
            while(!th.hasNext()){
                if(!sc.hasNextLine()) break;
                nextLine();
                code.append("\n");
            }

            String current = th.next().s;

            if(current.equals("}")) i--;
            else if(current.equals("{")) i++;

            if(i > 0) code.append(" ").append(current);
        }

        if(!th.current().equals("}")) err("Expected '}'");

        Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
        mod.statik = true;
        addMethod("main%[String", new KtjMethod(mod, "void", code.toString(), new KtjMethod.Parameter[]{new KtjMethod.Parameter(false, "[String", "args")}, uses, statics, getFileName(), _line));
    }

    private void parseModifier(String clazzName){
        if(th.current().equals("main")){
            parseMain();
            return;
        }

        Modifier mod = new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);

        switch (th.current().s){
            case "public":
                mod = new Modifier(AccessFlag.ACC_PUBLIC);
                break;
            case "private":
                mod = new Modifier(AccessFlag.ACC_PRIVATE);
                break;
            case "protected":
                mod = new Modifier(AccessFlag.ACC_PROTECTED);
                break;
            default:
                th.last();
                break;
        }

        Set<String> modifier = new HashSet<>();
        modifier.add("final");
        modifier.add("abstract");
        modifier.add("static");
        modifier.add("synchronised");
        modifier.add("const");

        while (modifier.contains(th.next().s)){
            switch (th.current().s){
                case "final":
                    if(mod.finaly) err("modifier "+th.current().s+" is not allowed");
                    mod.finaly = true;
                    break;
                case "abstract":
                    if(mod.abstrakt) err("modifier "+th.current().s+" is not allowed");
                    mod.abstrakt = true;
                    break;
                case "static":
                    if(mod.statik) err("modifier "+th.current().s+" is not allowed");
                    mod.statik = true;
                    break;
                case "synchronised":
                    if(mod.synchronised) err("modifier "+th.current().s+" is not allowed");
                    mod.synchronised = true;
                    break;
                case "const":
                    if(mod.constant) err("modifier "+th.current().s+" is not allowed");
                    mod.constant = true;
                    break;
            }
        }
        th.last();

        if(clazzName == null) {
            switch (th.assertToken(Token.Type.IDENTIFIER).s) {
                case "class":
                    parseClass(mod);
                    break;
                case "interface":
                    parseInterface(mod);
                    break;
                case "data":
                    parseData(mod);
                    break;
                case "type":
                    parseType(mod);
                    break;
                default:
                    th.last();
                    parseMethodAndField(mod, null);
                    break;
            }
        }else parseMethodAndField(mod, clazzName);
    }

    private void parseClass(Modifier modifier){
        String name = th.assertToken(Token.Type.IDENTIFIER).s;

        if(classes.containsKey(name)) err("Class "+name+" is already defined");

        KtjClass clazz = new KtjClass(modifier, uses, statics, getFileName(), line);
        current = clazz;

        if(th.isNext("extends")){
            clazz.superclass = th.assertToken(Token.Type.IDENTIFIER).s;

            ArrayList<String> interfaces = new ArrayList<>();

            while(th.isNext(",")){
                String in = th.assertToken(Token.Type.IDENTIFIER).s;

                if(!CompilerUtil.isInterface(in)) err("Expected interface got class "+in);
                if(interfaces.contains(in) || clazz.superclass.equals(in)) throw new RuntimeException("interface "+in+" is already extended");

                interfaces.add(in);
            }

            if(!interfaces.isEmpty()) clazz.interfaces = interfaces.toArray(new String[0]);
        }

        th.assertToken("{");
        th.assertNull();

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if (th.next().s.equals("}")) {
                    current = null;
                    th.assertNull();
                    classes.put(name, clazz);
                    return;
                } else {
                    parseModifier(name);
                }
            }
        }

        err("Expected '}'");
    }

    private void parseData(Modifier modifier){
        if(!modifier.isValidForData()) err("illegal modifier");

        String name = parseName();
        KtjDataClass clazz = new KtjDataClass(modifier, uses, statics, getFileName(), line);

        boolean constant = modifier.constant;
        modifier.constant = false;

        th.assertToken("=");
        th.assertToken("(");

        if(!th.next().equals(")")) {
            th.last();

            while (th.hasNext()) {
                if(th.isNext("const")){
                    if(clazz.addField(true, th.assertToken(Token.Type.IDENTIFIER).s, th.assertToken(Token.Type.IDENTIFIER).s, line))
                        err("field is already defined");
                }else if(clazz.addField(constant, th.assertToken(Token.Type.IDENTIFIER).s, th.assertToken(Token.Type.IDENTIFIER).s, line))
                    err("field is already defined");

                if (th.hasNext()){
                    if(th.assertToken(")", ",").equals(")")) break;
                    th.assertHasNext();
                }
            }
        }

        if(!th.current().equals(")")) err("Expected ')'");
        th.assertNull();

        classes.put(name, clazz);
    }

    private void parseType(Modifier modifier){
        if(!modifier.isValidForType()) err("illegal modifier");

        String name = parseName();

        th.assertToken("=");

        ArrayList<String> types = new ArrayList<>();
        types.add(th.assertToken(Token.Type.IDENTIFIER).s);

        while (th.hasNext()){
            th.assertToken("|");
            String type = th.assertToken(Token.Type.IDENTIFIER).s;

            if(!types.contains(type)) types.add(type);
        }

        classes.put(name, new KtjTypeClass(modifier, types.toArray(new String[0]), uses, statics, getFileName(), line));
    }

    private void parseInterface(Modifier modifier){
        if(!modifier.isValidForInterface()) err("illegal modifier");

        String name = parseName();

        th.assertToken("{");

        KtjInterface clazz = new KtjInterface(modifier, uses, statics, getFileName(), line);
        current = clazz;

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if(th.next().s.equals("}")){
                    current = null;
                    th.assertNull();
                    classes.put(name, clazz);
                    return;
                }else parseModifier(name);
            }
        }

        err("Expected '}'");
    }

    private String parseName(){
        String name = th.assertToken(Token.Type.IDENTIFIER).s;

        if(CompilerUtil.classExist(name)) err("Type Class "+name+" is already defined");

        return name;
    }

    private void parseMethodAndField(Modifier mod, String clazzName){
        String type = th.assertToken(Token.Type.IDENTIFIER).s;
        String name = th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR, "[", "(").s;

        while(name.equals("[")){
            th.assertToken("]");
            type = "["+type;
            name = th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR, "[").s;
        }

        if(name.equals("(")){
            if(type.equals(clazzName)) type = "<init>";
            parseMethod(mod, "void", type);
        }else{
            if(th.hasNext() && th.assertToken("=", "(").equals("(")) parseMethod(mod, type, name);
            else parseField(mod, type, name);
        }
    }

    private void parseField(Modifier mod, String type, String name){
        if(!mod.isValidForField()) err("illegal modifier");
        if(Lexer.isOperator(name.toCharArray()[0])) throw new RuntimeException("illegal argument");

        String initValue = null;

        if(th.current().equals("=")){
            StringBuilder sb = new StringBuilder();
            th.assertHasNext();

            while(th.hasNext()){
                sb.append(th.next().s).append(" ");
                if(th.current().equals(";") && th.hasNext()) throw new RuntimeException("illegal argument");
            }

            initValue = sb.toString();
        }

        if(mod.finaly && initValue == null) throw new RuntimeException("Expected init value for constant field "+name);

        if(current == null){
            mod.statik = true;
            if(statik.addField(name, new KtjField(mod, type, initValue, uses, statics, getFileName(), line))) err("field is already defined");
        }else if(current instanceof KtjClass){
            if(((KtjClass) current).addField(name, new KtjField(mod, type, initValue, uses, statics, path+"\\"+name, line))) err("field is already defined");
        }else throw new RuntimeException("illegal argument");
    }

    private void parseMethod(Modifier mod, String type, String name){
        if(name.equals("<init>")){
            if(!mod.isValidForInit()) err("illegal modifier");
        }else if(!mod.isValidForMethod()) err("illegal modifier");

        if(!name.equals("<init>") && Lexer.isOperator(name.toCharArray()[0])) {
            name = CompilerUtil.operatorToIdentifier(name);
            if(type.equals("void")) err("Method should not return void");
            if(mod.statik || current == null) err("Method should not be static");
            if(mod.accessFlag != AccessFlag.ACC_PUBLIC) err("Method should be public");
        }

        TokenHandler parameterList = th.getInBracket();
        ArrayList<KtjMethod.Parameter> parameter = new ArrayList<>();
        int _line = line;

        while(parameterList.hasNext()){
            boolean constant = parameterList.assertToken(Token.Type.IDENTIFIER).equals("const");
            String pType = constant ? parameterList.assertToken(Token.Type.IDENTIFIER).s : parameterList.current().s;
            String pName = parameterList.assertToken(Token.Type.IDENTIFIER, "[").s;

            while(pName.equals("[")){
                parameterList.assertToken("]");
                pType = "["+pType;
                pName = parameterList.assertToken(Token.Type.IDENTIFIER, "[").s;
            }

            for(KtjMethod.Parameter p:parameter) if(pName.equals(p.name)) err("Method "+pName+" is already defined");

            parameter.add(new KtjMethod.Parameter(constant, pType, pName));

            if(parameterList.hasNext()){
                parameterList.assertToken(",");
                parameterList.assertHasNext();
            }
        }

        StringBuilder desc = new StringBuilder(name);
        for(KtjMethod.Parameter p:parameter) desc.append("%").append(p.type);

        if(mod.abstrakt){
            th.assertNull();
            addMethod(desc.toString(), new KtjMethod(mod, type, null, new KtjMethod.Parameter[0], uses, statics, getFileName(), _line));
        }else if(th.isNext("{")){
            StringBuilder code = new StringBuilder();
            int i = 1;

            while(i != 0){
                while(!th.hasNext()){
                    if(!sc.hasNextLine()) break;
                    nextLine();
                    code.append("\n");
                }

                String current = th.next().s;

                if(current.equals("}")) i--;
                else if(current.equals("{")) i++;

                if(i > 0) code.append(" ").append(current);
            }

            if(!th.current().equals("}")) err("Expected '}'");

            if(!name.equals("<init>") && Lexer.isOperator(name.toCharArray()[0])) if(parameter.size() > 1) throw new RuntimeException("To many parameters");
            if(name.equals("->")) throw new RuntimeException("illegal method name");

            addMethod(desc.toString(), new KtjMethod(mod, type, code.toString(), parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), _line));
        }else if(th.isNext(":")){
            StringBuilder sb = new StringBuilder();
            boolean last = false;

            while(sc.hasNextLine() && !last){
                nextLine();
                if(!th.isEmpty()){
                    if(!th.isNext("(")) break;

                    TokenHandler ib = th.getInBracket();
                    StringBuilder arg = new StringBuilder();
                    if(ib.isEmpty()) err("Expected arguments");

                    for (KtjMethod.Parameter value : parameter) {
                        ib.assertToken(value.name);
                        if (!ib.isNext(",") && ib.hasNext()) {
                            if (!arg.toString().isEmpty()) arg.append("&&");
                            arg.append(value.name);
                            while (!ib.isNext(",") && ib.hasNext()) {
                                if (ib.isNext("(")) arg.append(ib.getInBracket().toStringNonMarked());
                                else arg.append(ib.next().s);
                            }
                        }
                    }

                    if(arg.toString().isEmpty()){
                        last = true;
                        if(!sb.toString().isEmpty()) sb.append("else ");
                        sb.append("if(true)");
                    }else{
                        if(!sb.toString().isEmpty()) sb.append("else ");
                        sb.append("if( ").append(arg).append(" )");
                    }

                    switch(th.assertToken("{", "=", "->").s){
                        case "->":
                            sb.append("->");
                            while(th.hasNext()){
                                sb.append(" ").append(th.next().s);

                                if(th.current().equals(";")) err("illegal argument ;");
                            }
                            sb.append("\n");
                            break;
                        case "=":
                            sb.append("->");
                            sb.append("return ");
                            while(th.hasNext()){
                                sb.append(" ").append(th.next().s);

                                if(th.current().equals(";")) err("illegal argument ;");
                            }
                            sb.append("\n");
                            break;
                        case "{":
                            int i = 1;

                            while(i != 0){
                                while(!th.hasNext()){
                                    if(!sc.hasNextLine()) break;
                                    nextLine();
                                    sb.append("\n");
                                }

                                String current = th.next().s;

                                if(current.equals("}")) i--;
                                else if(current.equals("{")) i++;

                                if(i > 0) sb.append(" ").append(current);
                            }

                            if(!th.current().equals("}")) err("Expected '}'");
                            th.assertNull();
                            break;
                    }
                }
            }

            if(sb.toString().isEmpty()) err("Expected ( got nothing");
            if(!last) err("Expected default");

            addMethod(desc.toString(), new KtjMethod(mod, type, sb.toString(), parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), _line));
        }else{
            StringBuilder code = new StringBuilder();

            if(th.assertToken("=", "->").equals("=")) code.append("return");

            while(th.hasNext()) code.append(" ").append(th.next());

            addMethod(desc.toString(), new KtjMethod(mod, type, code.toString(), parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), _line));
        }
    }

    private void addMethod(String desc, KtjMethod method){
        if(current == null){
            method.modifier.statik = true;
            if (statik.addMethod(desc, method)) err("method is already defined");
            return;
        }

        if (current instanceof KtjClass) {
            if (((KtjClass) current).addMethod(desc, method)) err("method is already defined");
        } else if (current instanceof KtjInterface) {
            if(!method.isAbstract()) err("expected abstract Method");
            if (((KtjInterface) current).addMethod(desc, method)) err("method is already defined");
        }
    }

    private String getFileName(){
        StringBuilder sb = new StringBuilder();

        if(!path.isEmpty()) sb.append(path).append("\\");
        sb.append(name);

        return sb.toString();
    }

    private void nextLine() throws RuntimeException{
        if(sc.hasNextLine()){
            th = Lexer.lex(sc.nextLine());
            line ++;
        }else throw new RuntimeException();
    }

    private void err(String message) throws RuntimeException{
        throw new RuntimeException(message+" at "+path+"\\"+name+":"+line);
    }
}
