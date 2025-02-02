# Analyze Emscripten export::dynCall_* functions to identify which table
# elements they call, and rename functions listed in the table by their dynCall
# type and index. These dynCall indices are often used as function pointers in
# compiled C/C++ code.
# @author nneonneo
# @category Analysis.Wasm
# @keybinding
# @menupath
# @toolbar

from __future__ import print_function
from ghidra.program.model.symbol import SourceType

l0 = currentProgram.getRegister("l0")
progspace = currentProgram.addressFactory.getAddressSpace("ram")
tablespace = currentProgram.addressFactory.getAddressSpace("table")
# We insert every dynCall index into a special namespace so that function pointers
# can be easily resolved.
# The format is dynCall::func_{calltype}_{index}.
dynCallNamespace = currentProgram.symbolTable.getOrCreateNameSpace(None, "dynCall", SourceType.USER_DEFINED)
dynCalls = {}

def getConst(inst):
    if inst.mnemonicString != "i32.const":
        raise Exception("Expected a constant")
    return inst.getOpObjects(0)[0].value

def getTableFunction(offset):
    funcAddr = getLong(tablespace.getAddress(offset * 8)) & 0xffffffff
    return getFunctionAt(progspace.getAddress(funcAddr))

def analyzeDyncall(function, calltype=None):
    if calltype is None:
        calltype = function.name.split("_", 1)[1]
    # Iterate instructions backwards
    instIterator = currentProgram.listing.getInstructions(function.body, False)
    for inst in instIterator:
        if inst.mnemonicString == "call_indirect":
            break
        elif inst.mnemonicString == "call":
            # forwarding to another function
            addr = inst.getOpObjects(0)[0]
            func = getFunctionAt(addr)
            # Note: name the new function in the global namespace,
            # unlike the parent function which is in the export namespace
            func.setName("dynCall_" + calltype, SourceType.USER_DEFINED)
            return analyzeDyncall(func, calltype)
    else:
        raise Exception("call_indirect not found")

    offset = 0
    mask = 0xffffffff
    while 1:
        inst = next(instIterator)
        if inst.mnemonicString == "i32.add":
            offset = getConst(next(instIterator))
        elif inst.mnemonicString == "i32.and":
            mask = getConst(next(instIterator))
        elif inst.mnemonicString == "i32.const":
            offset = getConst(inst)
            mask = 0
            break
        elif inst.mnemonicString == "local.get":
            if inst.getRegister(0) != l0:
                raise Exception("source is not l0?")
            break
        else:
            raise Exception("Unrecognized instruction " + str(inst))

    dynCalls[calltype] = (offset, mask)

def renameDyncalls(calltype):
    offset, mask = dynCalls.get(calltype, (0, 0))
    nullFunc = getTableFunction(offset)
    nullFunc.setName("nullFuncPtr_" + calltype, SourceType.USER_DEFINED)
    monitor.setMessage("Renaming " + calltype + " functions")
    monitor.initialize(mask)
    for i in range(mask+1):
        monitor.setProgress(i)
        func = getTableFunction(offset + i)
        name = "func_" + calltype + "_%d" % i
        if func.name.startswith("unnamed_function_"):
            func.setName(name, SourceType.ANALYSIS)
        currentProgram.symbolTable.createSymbol(func.entryPoint, name, dynCallNamespace, SourceType.USER_DEFINED)

for function in currentProgram.functionManager.getFunctions(True):
    if function.parentNamespace.name == "export" and function.name.startswith("dynCall_"):
        monitor.setMessage("Analyzing " + function.name)
        try:
            analyzeDyncall(function)
        except Exception as e:
            print("Failed to analyze %s: %s" % (function, e))

for calltype in dynCalls:
    renameDyncalls(calltype)
