# Kataja Compiler
Kataja Compiler, written in Java, to compile Kataja Code to Java Bytecode

## Branches
The Master Branch contains the latest (stable) Version of the Compiler. All other Branches representing a specified Version of the Compiler and the latest Version can contain bugs and/or could not provide all features.

## How to use
All features you need to know are described below, all other Classes and Methods are irrelevant for Compiling.

### General
All necessary methods you need are declared in the Compiler class ````com.github.ktj.compiler.Compiler````. To interact with the Compiler you need an object of that Class. The Compiler class has two static Methods that returns an object of the Compiler Class, ````Compiler.Instance()```` and ````Compiler.NewInstance()````. The NewInstance Method returns a new Object, while the Instance Method returns the last object created by the NewInstance Method, if NewInstance was not already called, it will be called within that Method.

The Compiler saves all recent compiled Classes of its Instance, if you want to build on previous compiled classes, you should call ````Instance()````. If you don't want the previous compiled classes to be included, you should call ````NewInstance()````.

### Output
For every new Compiler instance you should define an output Folder, in with the Compiled Classes will be writen in, with ````setOutFolder(String)````. The Method has one parameter, which defines the Path to the output Folder, if that Folder don't exist, a new one will be created

### Debug
To enable debug information you can use ````setDebug(boolean)````. The parameter defines whether debug information should be printed into the Console. The default value is ````false````. Is Debug is enabled, the Compiler prints information of the current compiling states into the Console.

### Compiling
To Compile a ktj File use ````compile(String, boolean, boolean)````. The first parameter defines the path to the kataja File, that should be compiled. The second parameter defines if that file should be executed after compiling has been finished. The third parameter defines if the output Folder should be cleared before the output will be written.

### !!!IMPORTANT: Note that if the output folder should be cleared all Files and Folders in it will be deleted. If compiling failed due to a syntax error for example the output Folder should not be touched!!!