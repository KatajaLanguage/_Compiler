package com.github.x.compiler;

import com.github.x.bytecode.AccessFlag;
import com.github.x.lang.Compilable;
import com.github.x.lang.Modifier;
import com.github.x.lang.KtjClass;
import com.github.x.lang.KtjTypeClass;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

final class Parser {

    private HashMap<String, Compilable> classes;
    private Scanner sc;
    private TokenHandler th;
    private String path;
    private String name;
    private int line;

    public HashMap<String, Compilable> parseFile(File file) throws IllegalArgumentException{
        if(!file.exists()) throw new IllegalArgumentException(STR."File \{file.getPath()} did not exist");

        try{
            path = file.getPath().substring(0, file.getPath().length() - file.getName().length() - 1);
            name = file.getName().substring(0, file.getName().length() - 4);
            classes = new HashMap<>();
            sc = new Scanner(file);
            line = 0;
        }catch(FileNotFoundException ignored){
            throw new IllegalArgumentException(STR."Unable to find \{file.getPath()}");
        }

        while(sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                switch (th.next().s()){
                    case "#" -> {}
                    case "public", "private", "protected" -> parseModifier(true);
                    case "class" -> parseClass(new Modifier(AccessFlag.ACC_PACKAGE_PRIVATE));
                    default -> err("illegal argument");
                }
            }
        }

        if(classes.size() == 1 && name.equals(classes.keySet().toArray(new String[0])[0])){
            String name = classes.keySet().toArray(new String[0])[0];
            Compilable clazz = classes.remove(name);
            classes.put(Compiler.validateClassName(STR."\{path}.\{name}"), clazz);
        }else{
            HashMap<String, Compilable> help = new HashMap<>();

            for(String clazz: classes.keySet()) help.put(Compiler.validateClassName(STR."\{path}.\{name}.\{clazz}"), classes.get(clazz));

            classes = help;
        }

        return classes;
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
                default -> err("illegal argument");
            }
        }else{
            err("illegal argument");
        }
    }

    private void parseClass(Modifier modifier){
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Class \{name} is already defined");

        th.assertToken("{");
        th.assertNull();

        KtjClass clazz = new KtjClass(modifier);

        while (sc.hasNextLine()){
            nextLine();

            if(!th.isEmpty()){
                switch (th.next().s()){
                    case "}" -> {
                        th.assertNull();
                        classes.put(name, clazz);
                        return;
                    }
                    default -> parseModifier(false);
                }
            }
        }

        err("Expected '}'");
    }

    private void parseInterface(Modifier modifier){

    }

    private void parseRecord(Modifier modifier){

    }

    private void parseEnum(Modifier modifier){

    }

    private void parseData(Modifier modifier){
        if(!modifier.isValidForData()) err("illegal modifier");

        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Data Class \{name} is already defined");

        th.assertToken("=");
        th.assertToken("[");

        while(th.hasNext() && !th.next().equals("]")){

        }

        if(!th.current().equals("]")) err("Expected ']'");
        th.assertNull();
    }

    private void parseType(Modifier modifier){
        if(!modifier.isValidForType()) err("illegal modifier");

        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(classes.containsKey(name)) err(STR."Type Class \{name} is already defined");

        th.assertToken("=");

        ArrayList<String> types = new ArrayList<>();
        types.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            String type = th.assertToken(Token.Type.IDENTIFIER).s();

            if(!types.contains(type)) types.add(type);
        }

        classes.put(name, new KtjTypeClass(modifier, types.toArray(new String[0])));
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
