package com.github.ktj.compiler;

import java.util.Scanner;

public final class Main {

    public static void main(String[] args){
        if(args.length > 0) executeArgs(args);
        else executeContinuousArgs();
    }

    private static void executeArgs(String[] args){
        String out = null;
        boolean execute = false;
        boolean debug = false;
        boolean clear = false;

        assert args.length % 2 != 0 : "illegal number of arguments";

        for(int i = 0;i < args.length - 1;i += 2){
            switch(args[i]){
                case "-o" -> out = args[i + 1];
                case "-e" -> execute = parseBoolean(args[i + 1]);
                case "-d" -> debug = parseBoolean(args[i + 1]);
                case "-c" -> clear = parseBoolean(args[i + 1]);
                default -> throw new RuntimeException(STR."illegal argument \{args[i]}");
            }
        }

        Compiler c = Compiler.NewInstance();
        c.setDebug(debug);
        if(out != null) c.setOutFolder(out);
        c.compile(args[args.length - 1], execute, clear);
    }

    private static void executeContinuousArgs(){
        Scanner sc = new Scanner(System.in);
        Compiler c = Compiler.NewInstance();

        boolean execute = false;
        boolean clear = false;
        boolean toClear = false;
        boolean quit = false;

        while(!quit){
            System.out.print("> ");
            String[] args = sc.nextLine().split(" ");

            for(int i = 0;i < args.length;i += 2){
                switch(args[i]){
                    case "-o" -> c.setOutFolder(args[i + 1]);
                    case "-d" -> c.setDebug(parseBoolean(args[i + 1]));
                    case "-e" -> execute = parseBoolean(args[i + 1]);
                    case "-q" -> {
                        quit = true;
                        i--;
                    }
                    case "-n" -> {
                        c = Compiler.NewInstance();
                        toClear = clear;
                    }
                    case "-c" -> {
                        clear = parseBoolean(args[i + 1]);
                        toClear = clear;
                    }
                    default -> {
                        if(i == args.length - 1 && !args[i].startsWith("-")) break;
                        throw new RuntimeException(STR."illegal argument \{args[i]} \{args[i + 1]}");
                    }
                }
            }

            if(args[args.length - 1].endsWith(".ktj")){
                c.compile(args[args.length - 1], execute, toClear);
                toClear = false;
            }
        }
    }

    private static boolean parseBoolean(String s){
        return switch(s){
            case "f", "false" -> false;
            case "t", "true" -> true;
            default -> throw new RuntimeException(STR."illegal argument \{s}");
        };
    }
}
