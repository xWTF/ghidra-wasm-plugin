package wasm.format.sections.structures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.StructConverter;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.program.model.data.DataType;
import ghidra.util.exception.DuplicateNameException;
import wasm.format.StructureBuilder;

public class WasmName implements StructConverter {
	private LEB128 size;
	private String value;

	public WasmName(BinaryReader reader) throws IOException {
		size = LEB128.readUnsignedValue(reader);
		byte[] data = reader.readNextByteArray((int) size.asLong());
		value = new String(data, StandardCharsets.UTF_8);
	}

	public long getSize() {
		return size.getLength() + size.asLong();
	}

	public String getValue() {
		return value;
	}

	@Override
	public DataType toDataType() throws DuplicateNameException, IOException {
		StructureBuilder builder = new StructureBuilder("name_" + size.asLong());
		builder.add(size, "size");
		builder.addString((int) size.asLong(), "value");
		return builder.toStructure();
	}
}
