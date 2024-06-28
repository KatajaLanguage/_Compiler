package com.github.ktj.compiler;

import java.util.Scanner;

public final class Main {

    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        boolean quit = false;

        while (!quit){
            Compiler c = Compiler.NewInstance();
            boolean debug = false;

            System.out.print("> ");
            args = sc.nextLine().split(" ");

            loop:
                if(args.length > 0){
                    int i = 0;

                    if(args[i].equals("-q")){
                        quit = true;
                        i++;
                    }

                    if(args.length <= i) break loop;

                    if(args[i].equals("-d")){
                        c.setDebug(true);
                        debug = true;
                        i++;
                    }

                    if(args.length <= i) break loop;

                    if(args[i].equals("-o")){
                        if(args.length == i){
                            System.out.println("Expected argument");
                            break loop;
                        }
                        c.setOutFolder(args[i + 1]);
                        i += 2;
                    }

                    if(args.length <= i) break loop;

                    if(args[i].equals("-c")){
                        if(args.length == i){
                            System.out.println("Expected argument");
                            break loop;
                        }
                        try {
                            c.compile(args[i + 1], false, true);
                        }catch(RuntimeException e){
                            System.out.println(e.getMessage());
                            if(debug){
                                for(int j = 0;j < e.getStackTrace().length;j++){
                                    System.out.println("\t"+e.getStackTrace()[j].toString());
                                }
                            }
                        }
                        i += 2;
                    }else if(args[i].equals("-e")){
                        if(args.length == i){
                            System.out.println("Expected argument");
                            break loop;
                        }
                        try {
                            c.compile(args[i + 1], true, true);
                        }catch(RuntimeException e){
                            System.out.println(e.getMessage());
                            if(debug){
                                for(int j = 0;j < e.getStackTrace().length;j++){
                                    System.out.println("\t"+e.getStackTrace()[j].toString());
                                }
                            }
                        }
                        i += 2;
                    }

                    if(args.length > i){
                        System.out.println("illegal argument(s)");
                    }
                }
        }
    }
}
