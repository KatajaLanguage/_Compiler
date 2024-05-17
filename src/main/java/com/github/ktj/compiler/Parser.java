package com.github.ktj.compiler;

import com.github.ktj.bytecode.AccessFlag;
import com.github.ktj.lang.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

final class Parser {

    private HashMap<String, Compilable> classes;
    private HashMap<String, String> uses;
    private Scanner sc;
    private TokenHandler th;
    private String path;
    private String name;
    private Compilable current = null;
    private KtjClass statik;
    private int line;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException(STR."File \{file.getPath()} did not exist");

        try{
            path = file.getPath().substring(0, file.getPath().length() - file.getName().length() - 1);
            name = file.getName().substring(0, file.getName().length() - 4);
            classes = new HashMap<>();
            uses = new HashMap<>();
            sc = new Scanner(file);
            statik = new KtjClass(new Modifier(AccessFlag.ACC_PUBLIC), uses);
            line = 0;
        }catch(FileNotFoundException ignored){
            throw new IllegalArgumentException(STR."Unable to find \{file.getPath()}");
        }

        while(sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                try {
                    switch (th.next().s()) {
                        case "#" -> {}
                        case "uses" -> parseUse();
                        default -> parseModifier(true);
                    }
                }catch(RuntimeException e){
                    if(e.getMessage().contains(" at ")) throw e;
                    else err(e.getMessage());
                }
            }
        }

        if(!statik.isEmpty()) {
            if (classes.size() == 1 && name.equals(classes.keySet().toArray(new String[0])[0]) && classes.get(name) instanceof KtjClass clazz) {
                for(String field:statik.fields.keySet()){
                    statik.fields.get(field).modifier.statik = true;
                    if(clazz.addField(field, statik.fields.get(field))) err(STR."Field \{field} is already defined in Class \{name}");
                }

                for(String method:statik.methods.keySet()){
                    statik.methods.get(method).modifier.statik = true;
                    if(clazz.addMethod(method, statik.methods.get(method))) err(STR."Method \{method} is already defined in Class \{name}");
                }
            } else classes.put(STR."_\{name}File", statik);
        }

        if(classes.size() == 1 && name.equals(classes.keySet().toArray(new String[0])[0])){
            String name = classes.keySet().toArray(new String[0])[0];
            Compilable clazz = classes.remove(name);
            classes.put(Compiler.validateClassName(STR."\{path}.\{name}"), clazz);

            if(uses.containsKey(name)) err(STR.".\{name} is already defined");
            uses.put(name, STR."\{Compiler.validateClassName(path)}.\{name}");
        }else{
            HashMap<String, Compilable> help = new HashMap<>();

            for(String clazz: classes.keySet()){
                help.put(Compiler.validateClassName(STR."\{path}.\{name}.\{clazz}"), classes.get(clazz));

                path = Compiler.validateClassName(path);
                if(uses.containsKey(clazz)) err(STR.".\{clazz} is already defined");
                uses.put(clazz, STR."\{path}.\{name}.\{clazz}");
            }

            classes = help;
        }

        return classes;
    }

    private void parseUse(){
        String current = th.assertToken(Token.Type.IDENTIFIER).s();

        if(th.assertToken("/", "from", ",").equals("/")){
            StringBuilder path = new StringBuilder(current);
            th.assertHasNext();

            while (th.hasNext()){
                if(th.assertToken("/", "as").equals("as")){
                    if(uses.containsKey(th.assertToken(Token.Type.IDENTIFIER).s())) err(STR."\{th.current()} is already defined");
                    uses.put(th.current().s(), path.toString());
                }else{
                    current = th.assertToken(Token.Type.IDENTIFIER).s();
                    path.append("/").append(current);
                }
            }

            if(uses.containsKey(current)) err(STR."\{current} is already defined");
            uses.put(current, path.toString());
        }else{
            ArrayList<String> classes = new ArrayList<>();
            classes.add(current);

            if(th.current().equals(",")){
                classes.add(th.assertToken(Token.Type.IDENTIFIER).s());
                th.assertToken(",", "from");
            }

            StringBuilder path = new StringBuilder(th.assertToken(Token.Type.IDENTIFIER).s());

            while (th.hasNext()){
                if(th.assertToken("/", "as").equals("as")){
                    if(classes.size() != 1) err("illegal argument");
                    if(uses.containsKey(th.assertToken(Token.Type.IDENTIFIER).s())) err(STR."\{th.current()} is already defined");
                    uses.put(th.current().s(), path + classes.getFirst());
                }else path.append("/").append(th.assertToken(Token.Type.IDENTIFIER).s());
            }

            for(String clazz:classes){
                if(uses.containsKey(clazz)) err(STR."\{clazz} is already defined");
                uses.put(clazz, path.toString());
            }
        }
    }

    private void parseModifier(boolean allowClasses){
        Modifier mod = switch (th.current().s()){
            case "public" -> new Modifier(AccessFlag.ACC_PUBLIC);
            case "private" -> new Modifier(AccessFlag.ACC_PRIVATE);
            case "protected" -> new Modifier(AccessFlag.ACC_PROTECTED);
            default -> {
                th.last();
                yield new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE);
            }
        };
        Set<String> modifier = Set.of("final", "abstract", "static", "synchronised", "const");

        while (modifier.contains(th.next().s())){
            switch (th.current().s()){
                case "final" -> {
                    if(mod.finaly) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.finaly = true;
                }
                case "abstract" -> {
                    if(mod.abstrakt) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.abstrakt = true;
                }
                case "static" -> {
                    if(mod.statik) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.statik = true;
                }
                case "synchronised" -> {
                    if(mod.synchronised) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.synchronised = true;
                }
                case "const" -> {
                    if(mod.constant) err(STR."modifier \{th.current().s()} is not allowed");
                    mod.constant = true;
                }
            }
        }
        th.last();

        if(allowClasses) {
            switch (th.assertToken(Token.Type.IDENTIFIER).s()) {
                case "class" -> parseClass(mod);
                case "interface" -> parseInterface(mod);
                case "record" -> parseRecord(mod);
                case "enum" -> parseEnum(mod);
                case "data" -> parseData(mod);
                case "type" -> parseType(mod);
                default -> {
                    th.last();
                    parseMethodAndField(mod);
                }
            }
        }else parseMethodAndField(mod);
    }

    private void parseClass(Modifier modifier){
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Class \{name} is already defined");

        th.assertToken("{");
        th.assertNull();

        KtjClass clazz = new KtjClass(modifier, uses);
        current = clazz;

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if (th.next().s().equals("}")) {
                    current = null;
                    th.assertNull();
                    classes.put(name, clazz);
                    return;
                } else {
                    parseModifier(false);
                }
            }
        }

        err("Expected '}'");
    }

    private void parseRecord(Modifier modifier){
        err("illegal argument");
    }

    private void parseEnum(Modifier modifier){
        err("illegal argument");
    }

    private void parseData(Modifier modifier){
        if(!modifier.isValidForData()) err("illegal modifier");

        String name = parseName();
        KtjDataClass clazz = new KtjDataClass(modifier, uses);

        th.assertToken("=");
        th.assertToken("[");

        if(!th.next().equals("]")) {
            th.last();

            while (th.hasNext()) {
                if (clazz.addField(th.assertToken(Token.Type.IDENTIFIER).s(), th.assertToken(Token.Type.IDENTIFIER).s()))
                    err("field is already defined");

                if (th.hasNext()){
                    if(th.assertToken("]", ",").equals("]")) break;
                    th.assertHasNext();
                }
            }
        }

        if(!th.current().equals("]")) err("Expected ']'");
        th.assertNull();

        classes.put(name, clazz);
    }

    private void parseType(Modifier modifier){
        if(!modifier.isValidForType()) err("illegal modifier");

        String name = parseName();

        th.assertToken("=");

        ArrayList<String> types = new ArrayList<>();
        types.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            String type = th.assertToken(Token.Type.IDENTIFIER).s();

            if(!types.contains(type)) types.add(type);
        }

        classes.put(name, new KtjTypeClass(modifier, types.toArray(new String[0]), uses));
    }

    private void parseInterface(Modifier modifier){
        if(!modifier.isValidForInterface()) err("illegal modifier");

        String name = parseName();

        th.assertToken("{");

        KtjInterface clazz = new KtjInterface(modifier, uses);
        current = clazz;

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                if(th.next().s().equals("}")){
                    current = null;
                    th.assertNull();
                    classes.put(name, clazz);
                    return;
                }else{
                    th.last();
                    parseModifier(false);
                }
            }
        }

        err("Expected '}'");
    }

    private String parseName(){
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Type Class \{name} is already defined");

        return name;
    }

    private void parseMethodAndField(Modifier mod){
        String type = th.assertToken(Token.Type.IDENTIFIER).s();
        String name = th.hasNext() ? th.next().s() : null;

        if(name == null)
            err("illegal argument");
        else if(name.equals("("))
            parseMethod(mod, "void", type);
        else{
            if(th.hasNext()){
                th.assertToken("(");
                parseField(mod, type, name);
            }else parseField(mod, type, name);
        }
    }

    private void parseField(Modifier mod, String type, String name){
        if(!mod.isValidForField()) err("illegal modifier");

        if(current == null){
            if(statik.addField(name, new KtjField(mod, type, uses))) err("field is already defined");
            return;
        }

        if(current instanceof KtjClass){
            if(((KtjClass) current).addField(name, new KtjField(mod, type, uses))) err("field is already defined");
        }
    }

    private void parseMethod(Modifier mod, String type, String name){
        if(!mod.isValidForMethod()) err("illegal modifier");

        if(type.equals("void")) type = null;

        th.getInBracket().assertNull();

        if(mod.abstrakt){
            th.assertNull();
            addMethod(name, new KtjMethod(mod, type, null, uses));
        }else {
            th.assertToken("{");

            StringBuilder code = new StringBuilder();

            while (sc.hasNextLine()) {
                nextLine();

                if (!th.isEmpty()) {
                    if (th.next().s().equals("}")) {
                        addMethod(name, new KtjMethod(mod, type, code.toString(), uses));
                        return;
                    } else {
                        if (!code.isEmpty()) code.append("\n");
                        code.append(th.toStringNonMarked());
                    }
                }
            }

            err("Expected '}'");
        }
    }

    private void addMethod(String desc, KtjMethod method){
        if(current == null){
            if (statik.addMethod(desc, method)) err("method is already defined");
            return;
        }

        if (current instanceof KtjClass) {
            if (((KtjClass) current).addMethod(desc, method)) err("method is already defined");
        } else if (current instanceof KtjInterface) {
            if (((KtjInterface) current).addMethod(desc, method)) err("method is already defined");
        }
    }

    private void nextLine() throws RuntimeException{
        if(sc.hasNextLine()){
            th = Lexer.lex(sc.nextLine());
            line ++;
        }else throw new RuntimeException();
    }

    private void err(String message) throws RuntimeException{
        throw new RuntimeException(STR."\{message} at \{path}\\\{name}:\{line}");
    }
}
