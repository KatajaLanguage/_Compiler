# Kataja Compiler
Kataja Compiler, written in Java, to compile Kataja Code to Java Bytecode

## Branches
The Master Branch contains the latest (stable) Version of the Compiler. All other Branches representing a specified Version of the Compiler and the latest Version can contain bugs and/or could not provide all features.

## How to use
First Download the latest Version ([here](https://github.com/XaverWeste/Kataja-Compiler/tree/master/releases)) and run the jar ````java -jar KatajaCompiler-0.8.5.jar````. The Compiler starts a command prompt and waits for arguments.

Every Argument has to be a combination of the arguments described below (Note that the order of arguments is fixed, in the Order of the list below, every argument is optional).

- ``-q`` quits the application after the execution of the current commands
- ``-d`` enables debug information
- ``-o String`` set the output folder to the given path
- ``-c String...`` compiles the files or folders with the given paths
- ``-e String`` compiles the file or folder with the given path and executes the main method
- ``-i String`` sets the input to the given File

### Example:

````-q -d -e src/test/kataja/HelloWorld.ktj````

This Command will enable debug, compile and execute the HelloWorld file in the test folder of the Compiler and the application will be quit after the execution has finished