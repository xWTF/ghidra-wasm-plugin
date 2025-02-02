package wasm.format.sections.structures;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.StructConverter;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.DuplicateNameException;
import wasm.WasmLoader;
import wasm.format.StructureBuilder;
import wasm.format.WasmEnums.ValType;
import wasm.format.WasmModule;

public class WasmElementSegment implements StructConverter {

	private int flags;
	private ElementSegmentMode mode;

	private LEB128 tableidx; /* if (flags & 3) == 2 */
	private ConstantExpression offset; /* if (flags & 1) == 0 */
	private LEB128 count;

	int elemkind; /* if (flags & 4) == 0 */
	private List<LEB128> funcidxs; /* if (flags & 4) == 0 */

	ValType elemtype; /* if (flags & 4) != 0 */
	private List<ConstantExpression> exprs; /* if (flags & 4) != 0 */

	public enum ElementSegmentMode {
		active,
		passive,
		declarative,
	}

	public WasmElementSegment(BinaryReader reader) throws IOException {
		flags = reader.readNextUnsignedByte();
		if ((flags & 3) == 2) {
			/* active segment with explicit table index */
			tableidx = LEB128.readUnsignedValue(reader);
		} else {
			/* tableidx defaults to 0 */
			tableidx = null;
		}

		if ((flags & 1) == 0) {
			/* active segment */
			mode = ElementSegmentMode.active;
			offset = new ConstantExpression(reader);
		} else if ((flags & 2) == 0) {
			mode = ElementSegmentMode.passive;
		} else {
			mode = ElementSegmentMode.declarative;
		}

		if ((flags & 3) == 0) {
			/* implicit element type */
			elemkind = 0;
			elemtype = ValType.funcref;
		} else {
			/* explicit element type */
			int typeCode = reader.readNextUnsignedByte();
			if ((flags & 4) == 0) {
				/* elemkind */
				elemkind = typeCode;
			} else {
				/* reftype */
				elemtype = ValType.fromByte(typeCode);
			}
		}

		count = LEB128.readUnsignedValue(reader);
		if ((flags & 4) == 0) {
			/* vector of funcidx */
			funcidxs = new ArrayList<>();
			for (int i = 0; i < count.asLong(); i++) {
				funcidxs.add(LEB128.readUnsignedValue(reader));
			}
		} else {
			/* vector of expr */
			exprs = new ArrayList<>();
			for (int i = 0; i < count.asLong(); i++) {
				exprs.add(new ConstantExpression(reader));
			}
		}
	}

	public ElementSegmentMode getMode() {
		return mode;
	}

	public long getTableIndex() {
		if (tableidx == null) {
			return 0;
		}
		return tableidx.asLong();
	}

	public Long getOffset() {
		if (offset == null) {
			return null;
		}
		return offset.asI32();
	}

	public ValType getElementType() {
		if ((flags & 4) == 0) {
			if (elemkind == 0) {
				return ValType.funcref;
			}
			return null;
		} else {
			return elemtype;
		}
	}

	public Address[] getAddresses(Program program, WasmModule module) {
		int count = (int) this.count.asLong();
		Address[] result = new Address[count];

		if (funcidxs != null) {
			for (int i = 0; i < count; i++) {
				long funcidx = funcidxs.get(i).asLong();
				result[i] = WasmLoader.getFunctionAddress(program, module, (int) funcidx);
			}
			return result;
		}

		if (exprs != null) {
			for (int i = 0; i < count; i++) {
				result[i] = exprs.get(i).asAddress(program, module);
			}
			return result;
		}
		return null;
	}

	public byte[] getInitData(WasmModule module) {
		int elemSize = getElementType().getSize();
		int count = (int) this.count.asLong();
		byte[] result = new byte[count * elemSize];
		Arrays.fill(result, (byte) 0xff);

		if (funcidxs != null) {
			for (int i = 0; i < count; i++) {
				long funcidx = funcidxs.get(i).asLong();
				long funcaddr = WasmLoader.getFunctionAddressOffset(module, (int) funcidx);
				byte[] v = ConstantExpression.longToBytes(funcaddr);
				System.arraycopy(v, 0, result, i * elemSize, elemSize);
			}
			return result;
		}

		if (exprs != null) {
			for (int i = 0; i < count; i++) {
				byte[] v = exprs.get(i).asBytes(module);
				if (v != null)
					System.arraycopy(v, 0, result, i * elemSize, elemSize);
			}
			return result;
		}
		return null;
	}

	@Override
	public DataType toDataType() throws DuplicateNameException, IOException {
		StructureBuilder builder = new StructureBuilder("element_segment");
		builder.add(BYTE, "flags");
		if (tableidx != null) {
			builder.add(tableidx, "tableidx");
		}
		if (offset != null) {
			builder.add(offset, "offset");
		}
		if ((flags & 3) != 0) {
			/* both elemkind and reftype are single bytes */
			builder.add(BYTE, "element_type");
		}

		builder.add(count, "count");
		if (funcidxs != null) {
			for (int i = 0; i < funcidxs.size(); i++) {
				builder.add(funcidxs.get(i), "element" + i);
			}
		}
		if (exprs != null) {
			for (int i = 0; i < exprs.size(); i++) {
				builder.add(exprs.get(i), "element" + i);
			}
		}

		return builder.toStructure();
	}
}
