# Kataja Compiler
Kataja Compiler, written in Java, to compile Kataja Code to Java Bytecode

## Branches
The Master Branch contains the latest (stable) Version of the Compiler. All other Branches representing a specified Version of the Compiler and the latest Version can contain bugs and/or could not provide all features.

## How to use
First Download the latest Version ([here](https://github.com/XaverWeste/Kataja-Compiler/tree/master/releases)). You can use the Compiler in two different ways, with and without arguments, as described below.

### With Arguments
The last Argument has to be the Path to the Kataja File that should be compiled. Before that you can set several options:
- -o String | path to the Output Folder, default = out
- -e boolean | execute main method, default = false
- -d boolean | debug, default = false
- -c boolean | clear output Folder before compiling, default = false

example:
````
java -jar KatajaCompiler.jar -o C:\Users\user\compiled -e true src/kataja/HelloWorld.ktj
````

### Without Arguments
If you run the Compiler with zero arguments the Compiler starts a Console application, where you can enter several commands in several statements:
- -o String | path to the Output Folder, default = out
- -e boolean | execute main method, default = false
- -d boolean | debug, default = false
- -c boolean | clear output Folder before compiling, default = false
- -n | creates a new Compiler Instance
- -q | quits the application after executing the current input

example:
````
java -jar KatajaCompiler.jar
> -d true -e true
> -o C:\Users\user\compiled
> -q -n src/kataja/HelloWorld.ktj
````