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
    private TokenHandler th;
    private String path;
    private String name;
    private Compilable current = null;
    private KtjObject statik;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException("File "+file.getPath()+" did not exist");

        try{
            if(0 < file.getPath().length() - file.getName().length() - 1) path = file.getPath().substring(0, file.getPath().length() - file.getName().length() - 1);
            else path = "";
            name = file.getName().substring(0, file.getName().length() - 4);
            classes = new HashMap<>();
            uses = new HashMap<>();
            statics = new ArrayList<>();
            th = Lexer.lex(file);
            statik = new KtjObject(new Modifier(AccessFlag.ACC_PUBLIC), uses, statics, getFileName(), 0);
        }catch(FileNotFoundException ignored){
            throw new IllegalArgumentException("Unable to find "+file.getPath());
        }catch(IndexOutOfBoundsException ignored){
            throw new IllegalArgumentException("Illegal argument " + file.getPath());
        }

        uses.put("Object", "java.lang.Object");
        uses.put("String", "java.lang.String");
        statik.superclass = "Object";

        while(th.hasNext()){
            switch (th.next().s){
                case ";":
                    break;
                case "use":
                    parseUse();
                    break;
                case "main":
                    parseMain();
                    break;
                default:
                    parseModifier(null);
                    break;
                }
        }

        if(!statik.isEmpty()){
            if(classes.containsKey(name)) err(name+" is already defined");
            statics.add(name);
            classes.put(name, statik);
        }

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
        String current = th.assertToken(Token.Type.IDENTIFIER, "$").s;

        if(current.equals("$"))
            current += th.assertToken(Token.Type.IDENTIFIER).s;

        if(th.assertToken("/", "from", ",").equals("/")){
            StringBuilder path = new StringBuilder(current);
            th.assertHasNext();
            th.last();

            while(th.isNext("/")){
                current = (current.startsWith("$") ? "$" : "") + th.assertToken(Token.Type.IDENTIFIER).s;
                path.append("/").append(current);
            }

            if(th.isNext("as")) addUse(th.assertToken(Token.Type.IDENTIFIER).s, path.toString());
            else addUse(current.split("/")[current.split("/").length - 1], path.toString().replace("/", "."));
        }else{
            ArrayList<String> classes = new ArrayList<>();
            classes.add(current);

            while(th.current().equals(",")){
                current = th.assertToken(Token.Type.IDENTIFIER, "$").s;

                if(current.equals("$"))
                    current += th.assertToken(Token.Type.IDENTIFIER).s;

                classes.add(current);
                th.assertToken(",", "from");
            }

            StringBuilder path = new StringBuilder(th.assertToken(Token.Type.IDENTIFIER).s);

            while(th.isNext("/")) path.append("/").append(th.assertToken(Token.Type.IDENTIFIER).s);

            if(th.isNext("as")){
                if(classes.size() != 1) err("illegal argument");
                addUse(th.assertToken(Token.Type.IDENTIFIER).s, path.toString().replace("/", ".")+"."+classes.get(0));
            }

            for(String clazz:classes) addUse(clazz, path.toString().replace("/", ".")+"."+clazz);
        }

        th.assertEndOfStatement();
    }

    private void addUse(String name, String path){
        if(path.contains("$")){
            StringBuilder sb = new StringBuilder();

            for(String s:path.split("\\$")) sb.append(s);

            path = sb.toString();
        }

        if(name.contains("$")) statics.add(name = name.substring(1));

        if(uses.containsKey(name)) err(name+" is already defined");
        uses.put(name, path);
    }

    private void parseMain(){
        if(th.isNext("->")){
            StringBuilder code = new StringBuilder();

            while(th.hasNext() && !th.isEndOfStatement()) code.append(" ").append(th.next());

            Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
            mod.statik = true;
            addMethod("main%[String", new KtjMethod(mod, null, "void", code.toString(), new KtjMethod.Parameter[]{new KtjMethod.Parameter(false, "[String", "args")}, uses, statics, getFileName(), th.getLine()));
            return;
        }

        th.assertToken("{");

        int _line = th.getLine();

        Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
        mod.statik = true;
        addMethod("main%[String", new KtjMethod(mod, null, "void", getInBracket(), new KtjMethod.Parameter[]{new KtjMethod.Parameter(false, "[String", "args")}, uses, statics, getFileName(), _line));
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
        modifier.add("volatile");
        modifier.add("transient");
        modifier.add("strict");
        modifier.add("native");

        while (modifier.contains(th.next().s)){
            switch (th.current().s){
                case "final":
                    if(mod.finaly) err("modifier "+ th.current().s+" is not allowed");
                    mod.finaly = true;
                    break;
                case "abstract":
                    if(mod.abstrakt) err("modifier "+ th.current().s+" is not allowed");
                    mod.abstrakt = true;
                    break;
                case "static":
                    if(mod.statik) err("modifier "+ th.current().s+" is not allowed");
                    mod.statik = true;
                    break;
                case "synchronised":
                    if(mod.synchronised) err("modifier "+ th.current().s+" is not allowed");
                    mod.synchronised = true;
                    break;
                case "const":
                    if(mod.constant) err("modifier "+ th.current().s+" is not allowed");
                    mod.constant = true;
                    break;
                case "volatile":
                    if(mod.volatil) err("modifier "+ th.current().s+" is not allowed");
                    mod.volatil = true;
                    break;
                case "transient":
                    if(mod.transint) err("modifier "+ th.current().s+" is not allowed");
                    mod.transint = true;
                    break;
                case "strict":
                    if(mod.strict) err("modifier "+ th.current().s+" is not allowed");
                    mod.strict = true;
                    break;
                case "native":
                    if(mod.natife) err("modifier "+ th.current().s+" is not allowed");
                    mod.natife = true;
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
                case "object":
                    parseObject(mod);
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

        if(!modifier.isValidForClass()) err("Illegal modifier");
        if(classes.containsKey(name)) err("Class "+name+" is already defined");

        ArrayList<GenericType> generics = parseGenerics();

        KtjClass clazz = new KtjClass(modifier, generics, uses, statics, getFileName(), th.getLine());
        current = clazz;

        if(th.isNext("extends")){
            clazz.superclass = th.assertToken(Token.Type.IDENTIFIER).s;

            ArrayList<String> interfaces = new ArrayList<>();

            while(th.isNext(",")){
                String in = th.assertToken(Token.Type.IDENTIFIER).s;

                if(interfaces.contains(in) || clazz.superclass.equals(in)) err("interface "+in+" is already extended");

                interfaces.add(in);
            }

            if(!interfaces.isEmpty()) clazz.interfaces = interfaces.toArray(new String[0]);
        }

        th.assertToken("{");

        while (th.hasNext()){
            if (th.next().s.equals("}")) {
                current = null;
                classes.put(name, clazz);
                return;
            }else parseModifier(name);
        }

        err("Expected '}'");
    }

    private void parseObject(Modifier modifier){
        String name = th.assertToken(Token.Type.IDENTIFIER).s;

        modifier.finaly = true;
        if(!modifier.isValidForObject()) err("Illegal modifier");
        if(modifier.accessFlag == AccessFlag.ACC_PRIVATE) err("Illegal modifier private");
        if(classes.containsKey(name)) err("Class "+name+" is already defined");

        KtjObject clazz = new KtjObject(modifier, uses, statics, getFileName(), th.getLine());
        current = clazz;

        th.assertToken("{");

        while (th.hasNext()){
            if (th.next().s.equals("}")) {
                current = null;
                classes.put(name, clazz);
                return;
            } else parseModifier(name);
        }

        err("Expected '}'");
    }

    private void parseData(Modifier modifier){
        if(modifier.accessFlag == AccessFlag.ACC_PRIVATE) err("Illegal modifier private");
        if(!modifier.isValidForData()) err("illegal modifier");

        String name = parseName();
        KtjDataClass clazz = new KtjDataClass(modifier, uses, statics, getFileName(), th.getLine());

        boolean constant = modifier.constant;
        modifier.constant = false;

        th.assertToken("=");
        th.assertToken("(");

        if(!th.next().equals(")")){
            th.last();

            while(th.hasNext()) {
                if(th.isNext("const")){
                    if(clazz.addField(true, th.assertToken(Token.Type.IDENTIFIER).s, th.assertToken(Token.Type.IDENTIFIER).s, th.getLine()))
                        err("field is already defined");
                }else if(clazz.addField(constant, th.assertToken(Token.Type.IDENTIFIER).s, th.assertToken(Token.Type.IDENTIFIER).s, th.getLine()))
                    err("field is already defined");

                if (th.hasNext()){
                    if(th.assertToken(")", ",").equals(")")) break;
                    th.assertHasNext();
                }
            }
        }

        if(!th.current().equals(")")) err("Expected ')'");
        th.assertEndOfStatement();

        classes.put(name, clazz);
    }

    private void parseType(Modifier modifier){
        if(modifier.accessFlag == AccessFlag.ACC_PRIVATE) err("Illegal modifier private");
        if(!modifier.isValidForType()) err("illegal modifier");

        String name = parseName();

        th.assertToken("=");

        ArrayList<String> types = new ArrayList<>();
        types.add(th.assertToken(Token.Type.IDENTIFIER).s);

        while(th.isNext("|")){
            String type = th.assertToken(Token.Type.IDENTIFIER).s;

            if(!types.contains(type)) types.add(type);
        }

        classes.put(name, new KtjTypeClass(modifier, types.toArray(new String[0]), uses, statics, getFileName(), th.getLine()));
        th.assertEndOfStatement();
    }

    private void parseInterface(Modifier modifier){
        if(modifier.accessFlag == AccessFlag.ACC_PRIVATE) err("Illegal modifier private");
        if(!modifier.isValidForInterface()) err("illegal modifier");

        String name = parseName();
        ArrayList<GenericType> generics = parseGenerics();

        th.assertToken("{");

        KtjInterface clazz = new KtjInterface(modifier, generics, uses, statics, getFileName(), th.getLine());
        current = clazz;

        while (th.hasNext()){
            if(th.next().s.equals("}")){
                current = null;
                classes.put(name, clazz);
                return;
            }else parseModifier(name);
        }

        err("Expected '}'");
    }

    private String parseName(){
        String name = th.assertToken(Token.Type.IDENTIFIER).s;

        if(CompilerUtil.classExist(name)) err("Type Class "+name+" is already defined");

        return name;
    }

    private ArrayList<GenericType> parseGenerics(){
        ArrayList<GenericType> generics = new ArrayList<>();

        if(th.isNext("<")){
            do{
                String typeName = th.assertToken(Token.Type.IDENTIFIER).s;
                String type = "Object";
                if(th.isNext("extends")) type = th.assertToken(Token.Type.IDENTIFIER).s;
                for(GenericType genericType:generics) if(genericType.name.equals(typeName)) err("Generic Type "+genericType.name);
                generics.add(new GenericType(typeName, type));
            }while(th.isNext(","));
            th.assertToken(">");
        }

        return generics;
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
            if(type.equals(clazzName)){
                parseMethod(mod, clazzName, "<init>");
            }else parseMethod(mod, "void", type);
        }else{
            if(th.hasNext() && th.assertToken("=", "(").equals("(")){
                if(name.equals("<init>")) err("illegal method name");
                parseMethod(mod, type, name);
            }else parseField(mod, type, name);
        }
    }

    private void parseField(Modifier mod, String type, String name){
        if(!mod.isValidForField()) err("illegal modifier");
        if(Lexer.isOperator(name.toCharArray()[0])) err("illegal argument");

        String initValue = null;

        if(th.current().equals("=")){
            StringBuilder sb = new StringBuilder();
            th.assertHasNext();

            while(!th.isEndOfStatement()) sb.append(th.next().s).append(" ");

            if(sb.toString().trim().isEmpty()) err("Expected value");

            initValue = sb.toString();
        }

        if(mod.finaly && initValue == null) err("Expected init value for constant field "+name);

        if(current == null){
            mod.statik = true;
            if(statik.addField(name, new KtjField(mod, type, null, initValue, uses, statics, getFileName(), th.getLine()))) err("field is already defined");
        }else if(current instanceof KtjObject){
            mod.statik = true;
            if(((KtjClass) current).addField(name, new KtjField(mod, type, null, initValue, uses, statics, path+"\\"+name, th.getLine()))) err("field is already defined");
        }else if(current instanceof KtjClass){
            if(((KtjClass) current).addField(name, new KtjField(mod, type, current.genericTypes, initValue, uses, statics, path+"\\"+name, th.getLine()))) err("field is already defined");
        }else err("illegal argument");
    }

    private void parseMethod(Modifier mod, String type, String name){
        if(name.equals("<init>")){
            if(!mod.isValidForInit()) err("illegal modifier");
            if(mod.statik) name = "<clinit>";
        }else if(!mod.isValidForMethod()) err("illegal modifier");

        if(!name.equals("<init>") && !name.equals("<clinit>") && Lexer.isOperator(name.toCharArray()[0])) {
            name = CompilerUtil.operatorToIdentifier(name);
            if(type.equals("void")) err("Method should not return void");
            if(mod.statik || current == null) err("Method should not be static");
            if(mod.accessFlag != AccessFlag.ACC_PUBLIC) err("Method should be public");
        }

        ArrayList<KtjMethod.Parameter> parameter = new ArrayList<>();

        if(!th.isNext(")")) {
            while (true) {
                boolean constant = th.assertToken(Token.Type.IDENTIFIER).equals("const");
                String pType = constant ? th.assertToken(Token.Type.IDENTIFIER).s : th.current().s;
                String pName = th.assertToken(Token.Type.IDENTIFIER, "[").s;

                while (pName.equals("[")) {
                    th.assertToken("]");
                    pType = "[" + pType;
                    pName = th.assertToken(Token.Type.IDENTIFIER, "[").s;
                }

                for (KtjMethod.Parameter p : parameter)
                    if (pName.equals(p.name)) err("Method " + pName + " is already defined");

                parameter.add(new KtjMethod.Parameter(constant, pType, pName));

                if (th.isNext(",")) th.assertHasNext();
                else {
                    th.assertToken(")");
                    break;
                }
            }
        }

        StringBuilder desc = new StringBuilder(name);
        for(KtjMethod.Parameter p:parameter) desc.append("%").append(p.type);

        if(!name.equals("<init>") && !name.equals("<clinit>") && Lexer.isOperator(name.toCharArray()[0])) if(parameter.size() > 1) err("To many parameters");
        if(name.equals("<clinit>") && (!parameter.isEmpty() || mod.accessFlag != AccessFlag.ACC_PACKAGE_PRIVATE)) err("Method should not be static");
        if(name.equals("->")) err("illegal method name");

        if(mod.abstrakt || mod.natife){
            th.assertEndOfStatement();
            addMethod(desc.toString(), new KtjMethod(mod, current != null ? current.genericTypes : null, type, null, parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), th.getLine()));
        }else if(th.isNext("{")){
            int _line = th.getLine();
            String code = getInBracket();

            addMethod(desc.toString(), new KtjMethod(mod, current != null ? current.genericTypes : null, type, code, parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), _line));
        }else if(th.isNext(":")){
            if(parameter.isEmpty()) err("Expected parameter");

            StringBuilder sb = new StringBuilder();
            boolean last = false;
            int _line = th.getLine();

            while(th.isNext("(") && !last){
                StringBuilder arg = new StringBuilder();
                for (KtjMethod.Parameter value : parameter) {
                    th.assertToken(value.name);
                    if(!th.isNext(",")){
                        if (!arg.toString().isEmpty()) arg.append("&&");
                        arg.append(value.name);
                        while (!th.isNext(",")){
                            if (th.isNext("(")) arg.append(getInBracket());
                            else arg.append(th.next().s);
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
                        while(!th.isEndOfStatement()) sb.append(" ").append(th.next().s);
                        sb.append("\n");
                        break;
                    case "=":
                        sb.append("-> return ");
                        while(!th.isEndOfStatement()) sb.append(" ").append(th.next().s);
                        sb.append("\n");
                        break;
                    case "{":
                        sb = new StringBuilder(getInBracket());
                        break;
                }
            }

            if(sb.toString().isEmpty()) err("Expected (");
            if(!last) err("Expected default");

            addMethod(desc.toString(), new KtjMethod(mod, current != null ? current.genericTypes : null, type, sb.toString(), parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), _line));
        }else{
            StringBuilder code = new StringBuilder();
            int _line = th.getLine();

            if(th.assertToken("=", "->").equals("=")) code.append("return");

            while(th.hasNext() && !th.isEndOfStatement()) code.append(" ").append(th.next());

            addMethod(desc.toString(), new KtjMethod(mod, current != null ? current.genericTypes : null, type, code.toString(), parameter.toArray(new KtjMethod.Parameter[0]), uses, statics, getFileName(), _line));
        }
    }

    private void addMethod(String desc, KtjMethod method){
        if(current == null){
            method.modifier.statik = true;
            if (statik.addMethod(desc, method)) err("method is already defined");
            return;
        }

        if(current instanceof KtjObject){
            method.modifier.statik = true;
            if(method.isAbstract()) err("method should not be abstract");
            if(desc.startsWith("<init>")) err("illegal method name");
            if (((KtjObject) current).addMethod(desc, method)) err("method is already defined");
        }else if (current instanceof KtjClass) {
            if (((KtjClass) current).addMethod(desc, method)) err("method is already defined");
        } else if (current instanceof KtjInterface) {
            if(!method.isAbstract()) err("expected abstract Method");
            if (((KtjInterface) current).addMethod(desc, method)) err("method is already defined");
        }
    }

    private String getInBracket(){
        String openingBracket = th.current().s;
        String closingBracket;
        switch(openingBracket){
            case "(":
                closingBracket = ")";
                break;
            case "[":
                closingBracket = "]";
                break;
            case "{":
                closingBracket = "}";
                break;
            case "<":
                closingBracket = ">";
                break;
            default:
                throw new RuntimeException();
        }
        StringBuilder sb = new StringBuilder();

        int line = th.getLine();
        int b = 1;

        while(b > 0 && th.hasNext()){
            th.next();
            while(th.getLine() != line){
                line++;
                sb.append("\n");
            }
            if(th.current().equals(openingBracket)) b++;
            else if(th.current().equals(closingBracket)) b--;
            if(b > 0) sb.append(th.current()).append(" ");
        }

        if(b > 0) err("Expected "+closingBracket);

        return sb.toString();
    }

    private String getFileName(){
        StringBuilder sb = new StringBuilder();

        if(!path.isEmpty()) sb.append(path).append("\\");
        sb.append(name);

        return sb.toString();
    }

    private void err(String message) throws ParsingException{
        throw new ParsingException(message, getFileName(), th.getLine());
    }
}
