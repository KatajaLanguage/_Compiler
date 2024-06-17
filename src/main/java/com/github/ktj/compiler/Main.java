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
                case "-o":
                    out = args[i + 1];
                    break;
                case "-e":
                    execute = parseBoolean(args[i + 1]);
                    break;
                case "-d":
                    debug = parseBoolean(args[i + 1]);
                    break;
                case "-c":
                    clear = parseBoolean(args[i + 1]);
                    break;
                default:
                    new RuntimeException("illegal argument "+args[i]).printStackTrace();
                    break;
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
                switch (args[i]){
                    case "-o":
                        c.setOutFolder(args[i + 1]);
                        break;
                    case "-d":
                        c.setDebug(parseBoolean(args[i + 1]));
                        break;
                    case "-e":
                        execute = parseBoolean(args[i + 1]);
                        break;
                    case "-q":
                        quit = true;
                        i--;
                        break;
                    case "-n":
                        c = Compiler.NewInstance();
                        toClear = clear;
                        break;
                    case "-c":
                        clear = parseBoolean(args[i + 1]);
                        toClear = clear;
                        break;
                    default:
                        if(i == args.length - 1 && !args[i].startsWith("-")) break;
                        new RuntimeException("illegal argument "+args[i]).printStackTrace();
                        break;
                }
            }

            if(args[args.length - 1].endsWith(".ktj")){
                try {
                    c.compile(args[args.length - 1], execute, toClear);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean parseBoolean(String s){
        switch (s){
            case "t":
            case "true":
                return true;
            default:
                new RuntimeException("illegal argument "+s).printStackTrace();
            case "f":
            case "false":
                return false;
        }
    }
}
