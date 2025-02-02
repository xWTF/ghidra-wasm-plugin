package wasm.format.sections;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.util.exception.DuplicateNameException;
import wasm.format.StructureBuilder;
import wasm.format.WasmEnums.WasmExternalKind;
import wasm.format.sections.structures.WasmExportEntry;

public class WasmExportSection extends WasmSection {

	private LEB128 count;
	private List<WasmExportEntry> exportList = new ArrayList<>();
	private Map<WasmExternalKind, List<WasmExportEntry>> exports = new EnumMap<>(WasmExternalKind.class);

	public WasmExportSection(BinaryReader reader) throws IOException {
		super(reader);
		count = LEB128.readUnsignedValue(reader);
		for (int i = 0; i < count.asLong(); ++i) {
			WasmExportEntry entry = new WasmExportEntry(reader);
			WasmExternalKind kind = entry.getKind();
			if (!exports.containsKey(kind)) {
				exports.put(kind, new ArrayList<WasmExportEntry>());
			}
			exports.get(kind).add(entry);
			exportList.add(entry);
		}
	}

	public List<WasmExportEntry> getExports(WasmExternalKind kind) {
		return exports.getOrDefault(kind, Collections.emptyList());
	}

	public WasmExportEntry findEntry(WasmExternalKind kind, int id) {
		for (WasmExportEntry entry : getExports(kind)) {
			if (entry.getIndex() == id) {
				return entry;
			}
		}
		return null;
	}

	@Override
	protected void addToStructure(StructureBuilder builder) throws DuplicateNameException, IOException {
		builder.add(count, "count");
		for (int i = 0; i < exportList.size(); i++) {
			builder.add(exportList.get(i), "export_" + i);
		}
	}

	@Override
	public String getName() {
		return ".export";
	}
}
