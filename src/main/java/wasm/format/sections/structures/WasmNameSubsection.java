package wasm.format.sections.structures;

import java.io.IOException;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.StructConverter;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.program.model.data.DataType;
import ghidra.util.exception.DuplicateNameException;
import wasm.format.StructureBuilder;

public abstract class WasmNameSubsection implements StructConverter {

	protected int id;
	private LEB128 contentLength;
	private long sectionOffset;

	// see https://github.com/WebAssembly/extended-name-section/blob/main/proposals/extended-name-section/Overview.md
	public enum WasmNameSubsectionId {
		NAME_MODULE,
		NAME_FUNCTION,
		NAME_LOCAL,
		NAME_LABELS,
		NAME_TYPE, 
		NAME_TABLE, 
		NAME_MEMORY, 
		NAME_GLOBAL, 
		NAME_ELEM,
		NAME_DATA
	}

	public static WasmNameSubsection createSubsection(BinaryReader reader) throws IOException {
		long sectionOffset = reader.getPointerIndex();
		int id = reader.readNextUnsignedByte();
		LEB128 contentLength = LEB128.readUnsignedValue(reader);
		reader.setPointerIndex(reader.getPointerIndex() + contentLength.asLong());

		BinaryReader sectionReader = reader.clone(sectionOffset);

		if (id >= WasmNameSubsectionId.values().length) {
			return new WasmNameUnknownSubsection(sectionReader);
		}

		switch (WasmNameSubsectionId.values()[id]) {
		case NAME_MODULE:
			return new WasmNameModuleSubsection(sectionReader);
		case NAME_FUNCTION:
			return new WasmNameMapSubsection("function", sectionReader);
		case NAME_LOCAL:
			return new WasmNameLocalSubsection(sectionReader);
		case NAME_LABELS:
			// TODO: not supported at the moment
			return new WasmNameUnknownSubsection(sectionReader);
		case NAME_TYPE:
			return new WasmNameMapSubsection("type", sectionReader);
		case NAME_TABLE:
			return new WasmNameMapSubsection("table", sectionReader);
		case NAME_MEMORY:
			return new WasmNameMapSubsection("memory", sectionReader);
		case NAME_GLOBAL:
			return new WasmNameMapSubsection("global", sectionReader);
		case NAME_ELEM:
			return new WasmNameMapSubsection("elem", sectionReader);
		case NAME_DATA:
			return new WasmNameMapSubsection("data", sectionReader);
		default:
			return new WasmNameUnknownSubsection(sectionReader);
		}
	}

	protected WasmNameSubsection(BinaryReader reader) throws IOException {
		sectionOffset = reader.getPointerIndex();
		id = reader.readNextUnsignedByte();
		contentLength = LEB128.readUnsignedValue(reader);
	}

	@Override
	public DataType toDataType() throws DuplicateNameException, IOException {
		StructureBuilder builder = new StructureBuilder(getName());
		builder.add(BYTE, "id");
		builder.add(contentLength, "size");
		addToStructure(builder);
		return builder.toStructure();
	}

	public abstract String getName();

	protected abstract void addToStructure(StructureBuilder builder) throws DuplicateNameException, IOException;

	public WasmNameSubsectionId getId() {
		if (id < WasmNameSubsectionId.values().length) {
			return WasmNameSubsectionId.values()[id];
		}
		return null;
	}

	public long getSectionOffset() {
		return sectionOffset;
	}

	public long getContentSize() {
		return contentLength.asLong();
	}

	public long getSectionSize() {
		return 1 + contentLength.getLength() + contentLength.asLong();
	}
}
