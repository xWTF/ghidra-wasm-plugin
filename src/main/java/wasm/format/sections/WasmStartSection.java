package wasm.format.sections;

import java.io.IOException;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.util.exception.DuplicateNameException;
import wasm.format.StructureBuilder;

public class WasmStartSection extends WasmSection {

	private LEB128 funcIdx;

	public WasmStartSection(BinaryReader reader) throws IOException {
		super(reader);
		funcIdx = LEB128.readUnsignedValue(reader);
	}

	public long getStartFunctionIndex() {
		return funcIdx.asLong();
	}

	@Override
	public void addToStructure(StructureBuilder builder) throws DuplicateNameException, IOException {
		builder.add(funcIdx, "func");
	}

	@Override
	public String getName() {
		return ".start";
	}
}
