package wasm.format.sections.structures;

import java.io.IOException;

import ghidra.app.util.bin.BinaryReader;
import ghidra.app.util.bin.StructConverter;
import ghidra.app.util.bin.format.dwarf4.LEB128;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.Float4DataType;
import ghidra.program.model.data.Float8DataType;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.DuplicateNameException;
import wasm.WasmLoader;
import wasm.format.StructureBuilder;
import wasm.format.WasmModule;

/* A reader for expressions containing a single constant instruction.

In principle, constant expressions could contain more than one 

Such expressions consist of an instruction from the following list:
- t.const c
- ref.null
- ref.func x
- global.get x
followed by an explicit end byte (0x0b).
*/
public final class ConstantExpression implements StructConverter {

	private ConstantInstruction type;
	private Object value;

	private enum ConstantInstruction {
		I32_CONST, /* i32.const n: value is LEB128 */
		I64_CONST, /* i64.const n: value is LEB128 */
		F32_CONST, /* f32.const z: value is byte[4] */
		F64_CONST, /* f64.const z: value is byte[8] */
		REF_NULL_FUNCREF, /* ref.null funcref: value is null */
		REF_NULL_EXTERNREF, /* ref.null externref: value is null */
		REF_FUNC, /* ref.func x: value is LEB128 funcidx */
		GLOBAL_GET, /* global.get x: value is LEB128 globalidx */
	}

	public ConstantExpression(BinaryReader reader) throws IOException, IllegalArgumentException {
		int typeCode = reader.readNextUnsignedByte();

		switch (typeCode) {
		case 0x23:
			type = ConstantInstruction.GLOBAL_GET;
			value = LEB128.readUnsignedValue(reader);
			break;
		case 0x41:
			type = ConstantInstruction.I32_CONST;
			value = LEB128.readUnsignedValue(reader);
			break;
		case 0x42:
			type = ConstantInstruction.I64_CONST;
			value = LEB128.readUnsignedValue(reader);
			break;
		case 0x43:
			type = ConstantInstruction.F32_CONST;
			value = reader.readNextByteArray(4);
			break;
		case 0x44:
			type = ConstantInstruction.F64_CONST;
			value = reader.readNextByteArray(8);
			break;
		case 0xD0: {
			int refTypeCode = reader.readNextUnsignedByte();
			if (refTypeCode == 0x6F) {
				type = ConstantInstruction.REF_NULL_EXTERNREF;
			} else if (refTypeCode == 0x70) {
				type = ConstantInstruction.REF_NULL_FUNCREF;
			} else {
				throw new IllegalArgumentException("Invalid ref.null reftype " + refTypeCode);
			}
			value = null;
			break;
		}
		case 0xD2:
			type = ConstantInstruction.REF_FUNC;
			value = LEB128.readUnsignedValue(reader);
			break;
		default:
			throw new IllegalArgumentException("Invalid instruction opcode " + typeCode + " in constant expression");
		}

		int end = reader.readNextUnsignedByte();
		if (end != 0x0b) {
			throw new IllegalArgumentException("Missing end byte");
		}
	}

	public static byte[] intToBytes(int value) {
		byte[] result = new byte[4];
		for (int i = 0; i < 4; i++) {
			result[i] = (byte) value;
			value >>= 8;
		}
		return result;
	}

	public static byte[] longToBytes(long value) {
		byte[] result = new byte[8];
		for (int i = 0; i < 8; i++) {
			result[i] = (byte) value;
			value >>= 8;
		}
		return result;
	}

	/**
	 * Return the bytes that correspond to the value produced, i.e. 4 bytes for
	 * i32.const, 8 bytes for ref.null, etc. Return null if the initializer cannot
	 * be determined (e.g. global) This needs a reference to the module so that
	 * function references can be resolved to their static addresses.
	 */
	public byte[] asBytes(WasmModule module) {
		switch (type) {
		case I32_CONST:
			return intToBytes((int) ((LEB128) value).asLong());
		case I64_CONST:
			return longToBytes(((LEB128) value).asLong());
		case REF_FUNC:
			return longToBytes(WasmLoader.getFunctionAddressOffset(module, (int) ((LEB128) value).asLong()));
		case F32_CONST:
		case F64_CONST:
			return (byte[]) value;
		case REF_NULL_FUNCREF:
		case REF_NULL_EXTERNREF:
			return new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		case GLOBAL_GET:
			return null;
		default:
			return null;
		}
	}

	public Address asAddress(Program program, WasmModule module) {
		if (type == ConstantInstruction.REF_FUNC) {
			return WasmLoader.getFunctionAddress(program, module, (int) ((LEB128) value).asLong());
		}
		return null;
	}

	public Long asI32() {
		if (type == ConstantInstruction.I32_CONST) {
			return ((LEB128) value).asLong();
		}
		return null;
	}

	public Long asGlobalGet() {
		if (type == ConstantInstruction.GLOBAL_GET) {
			return ((LEB128) value).asLong();
		}
		return null;
	}

	@Override
	public DataType toDataType() throws DuplicateNameException, IOException {
		StructureBuilder builder = new StructureBuilder("expr");
		builder.add(BYTE, "opcode");
		switch (type) {
		case I32_CONST:
		case I64_CONST:
		case REF_FUNC:
		case GLOBAL_GET:
			builder.add((LEB128) value, "value");
			break;
		case F32_CONST:
			builder.add(Float4DataType.dataType, "value");
			break;
		case F64_CONST:
			builder.add(Float8DataType.dataType, "value");
			break;
		case REF_NULL_FUNCREF:
		case REF_NULL_EXTERNREF:
			builder.add(BYTE, "nulltype");
			break;
		}
		builder.add(BYTE, "end_opcode");
		return builder.toStructure();
	}
}
