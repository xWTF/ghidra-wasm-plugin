package wasm.format.sections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.util.exception.DuplicateNameException;
import wasm.format.StructureBuilder;
import wasm.format.sections.structures.WasmElementSegment;

public class WasmElementSection extends WasmSection {

	private LEB128 count;
	private List<WasmElementSegment> elements = new ArrayList<WasmElementSegment>();

	public WasmElementSection(BinaryReader reader) throws IOException {
		super(reader);
		count = LEB128.readUnsignedValue(reader);
		for (int i = 0; i < count.asLong(); ++i) {
			elements.add(new WasmElementSegment(reader));
		}
	}

	public List<WasmElementSegment> getSegments() {
		return Collections.unmodifiableList(elements);
	}

	@Override
	protected void addToStructure(StructureBuilder builder) throws DuplicateNameException, IOException {
		builder.add(count, "count");
		for (int i = 0; i < elements.size(); i++) {
			builder.add(elements.get(i), "element_" + i);
		}
	}

	@Override
	public String getName() {
		return ".element";
	}
}
