package wasm.format.sections.structures;

import java.io.IOException;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.StructConverter;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.DuplicateNameException;
import wasm.format.StructureBuilder;
import wasm.format.WasmModule;

public class WasmGlobalEntry implements StructConverter {

	private WasmGlobalType type;
	private ConstantExpression expr;

	public WasmGlobalEntry(BinaryReader reader) throws IOException {
		type = new WasmGlobalType(reader);
		expr = new ConstantExpression(reader);
	}

	public WasmGlobalType getGlobalType() {
		return type;
	}

	public byte[] asBytes(WasmModule module) {
		return expr.asBytes(module);
	}

	public Address asAddress(Program program, WasmModule module) {
		return expr.asAddress(program, module);
	}

	public Long asGlobalGet() {
		return expr.asGlobalGet();
	}

	@Override
	public DataType toDataType() throws DuplicateNameException, IOException {
		StructureBuilder builder = new StructureBuilder("global_entry");
		builder.add(type, "type");
		builder.add(expr, "expr");
		return builder.toStructure();
	}
}
